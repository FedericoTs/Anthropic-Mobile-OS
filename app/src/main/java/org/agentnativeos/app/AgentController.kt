package org.agentnativeos.app

import android.content.Intent
import android.util.Log
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.label
import org.agentnativeos.core.model.AppInfo
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.AuditLog
import org.agentnativeos.core.events.Correlation
import org.agentnativeos.core.events.EventConsumer
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.loop.AgentLoop
import org.agentnativeos.core.loop.ConfirmationHandler
import org.agentnativeos.core.loop.LoopResult
import org.agentnativeos.core.memory.TaskRecord
import org.agentnativeos.core.memory.TaskStatus
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.undo.UndoStack
import java.util.concurrent.Executors

/**
 * Wires the core AgentLoop to the device (the AccessibilityService is the
 * Perceiver + Actuator) and runs it off the main thread. Narration is streamed to
 * logcat under the AGENT tag and kept in an in-memory audit; a UI feed can
 * subscribe to the same bus.
 */
object AgentController {

    private const val TAG = "AGENT"
    private val executor = Executors.newSingleThreadExecutor()

    fun run(
        intent: String,
        provider: ModelProvider,
        confirm: ConfirmationHandler,
        onResult: (LoopResult) -> Unit = {},
    ) {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "service not connected")
            onResult(LoopResult.Aborted("agent service not enabled", "nothing yet", 0))
            return
        }
        AgentSession.begin()
        val undo = UndoStack()
        AgentSession.lastUndoStack = undo
        val memory = PersistentTaskMemory(service)
        executor.execute {
            val bus = InMemoryEventBus()
            val audit = AuditLog()
            bus.subscribe(audit)
            bus.subscribe(EventConsumer { event -> Log.i(TAG, event.audit()) })
            bus.subscribe(EventConsumer { event -> AgentSession.emit(event) })
            val apps = installedApps(service)
            val capabilities = DeviceCapabilities.discover(service)

            // E2 stretch (Phase 5): a compound intent fans out to parallel sub-agents.
            // Cheap connective prefilter first; the decomposer model has the final say
            // (it returns ONE goal for a false positive, and any error falls back to
            // the single-agent loop — fail safe).
            val goals = if (org.agentnativeos.core.model.Compound.looksCompound(intent)) {
                provider.decompose(intent)
            } else {
                listOf(intent)
            }

            val result = if (goals.size > 1) {
                runCoordinated(service, provider, confirm, bus, goals, apps, capabilities)
            } else {
                AgentLoop(
                    perceiver = service,
                    provider = provider,
                    actuator = service,
                    gate = PolicyGate(),
                    confirmer = confirm,
                    bus = bus,
                    clock = { System.currentTimeMillis() },
                    // Real devices animate: wait after each action so the next perceive
                    // reads the settled screen (e.g. the launcher after "home"), not a
                    // transitional one — and re-plan if a target goes stale mid-flight.
                    settleMs = 800L,
                    idle = { ms -> try { Thread.sleep(ms) } catch (_: InterruptedException) {} },
                    // Let the planner launch apps directly by package instead of hunting
                    // for a (possibly localized) icon on the launcher.
                    availableApps = apps,
                    // Direct fast paths, filtered to what THIS device can actually handle.
                    capabilities = capabilities,
                    // What the agent has done before — continuity + "what have you done?".
                    recentTasks = memory.recent(8),
                    cancelled = { AgentSession.stopRequested },
                    undo = undo,
                ).run(intent)
            }
            memory.record(toRecord(intent, result))
            Log.i(TAG, "result: $result")
            AgentSession.finish(result)
            onResult(result)
        }
    }

    /**
     * Multi-agent run: one sub-agent per goal, parallel planning, actuation serialized
     * on the one screen by the core Coordinator (stale re-plan at dequeue, consolidated
     * confirm, conflict blocking). Partial results are narrated honestly per goal.
     */
    private fun runCoordinated(
        service: AgentAccessibilityService,
        provider: ModelProvider,
        confirm: ConfirmationHandler,
        bus: InMemoryEventBus,
        goals: List<String>,
        apps: List<AppInfo>,
        capabilities: List<org.agentnativeos.core.model.Capability>,
    ): LoopResult {
        val now = { System.currentTimeMillis() }
        val corr = Correlation("multi", 0)
        bus.emit(
            NarrationEvent.Plan(
                corr, now(),
                "splitting into ${goals.size} goals: ${goals.joinToString("  ·  ")}",
            ),
        )
        val agents = goals.mapIndexed { i, goal ->
            org.agentnativeos.core.multiagent.ModelSubAgent(
                id = "g${i + 1}",
                goal = goal,
                provider = provider,
                availableApps = apps,
                capabilities = capabilities,
                bus = bus,
                clock = now,
                cancelled = { AgentSession.stopRequested },
            )
        }
        // The scheduler owns the one screen; settle after each real act so the next
        // perceive reads a stable tree (same 800ms rhythm as the single loop). Execute
        // events keep the audit stream complete.
        val settlingActuator = org.agentnativeos.core.loop.Actuator { action ->
            val outcome = service.act(action)
            bus.emit(NarrationEvent.Execute(corr, now(), action.label(), outcome.ok))
            try { Thread.sleep(800L) } catch (_: InterruptedException) {}
            outcome
        }
        // ONE consolidated review for all high-side-effect actions in a batch:
        // all-or-nothing through the same ConfirmationHandler (overlay/autonomy included).
        val batchConfirm = org.agentnativeos.core.multiagent.BatchConfirm { review ->
            val combined = review.items.joinToString("; ") { "[${it.agentId}] ${it.action.label()}" }
            val approved = confirm.confirm(review.items.first().action, "This will commit: $combined")
            bus.emit(NarrationEvent.Confirm(corr, now(), combined, approved))
            if (approved) review.items.map { it.agentId }.toSet() else emptySet()
        }
        val outcome = org.agentnativeos.core.multiagent.Coordinator(
            perceiver = service,
            actuator = settlingActuator,
            gate = PolicyGate(),
            maxRounds = 25,
            idle = { try { Thread.sleep(800L) } catch (_: InterruptedException) {} },
        ).run(agents, batchConfirm)

        // Honest per-goal summary — partial results are first-class, never rounded up.
        val parts = agents.joinToString("\n") { agent ->
            val mark = if (agent.finishedOk) "✓" else "⚠"
            "$mark ${agent.goal} — ${agent.outcome ?: "unfinished"}"
        }
        val allDone = agents.all { it.finishedOk } && outcome.unfinished.isEmpty()
        return if (allDone) {
            bus.emit(NarrationEvent.Done(corr, now(), parts))
            LoopResult.Completed(parts, outcome.actuations.size)
        } else {
            bus.emit(NarrationEvent.Failure(corr, now(), "some goals didn't finish", parts))
            LoopResult.Aborted("partial: some goals didn't finish", parts, outcome.actuations.size)
        }
    }

    /** CI / demo: run a fixed scripted plan (no model, no network). */
    fun runScripted(intent: String, script: List<AgentAction>, onResult: (LoopResult) -> Unit = {}) {
        run(intent, ScriptedModelProvider(script), ConfirmationHandler { _, _ -> false }, onResult)
    }

    /**
     * Rewind the last run: replay each step's inverse (newest first), stopping at an
     * irreversible barrier. The inverses are all low-side-effect (Back / restore text /
     * scroll back), so they skip the gate. Narrated into the same feed for transparency.
     */
    fun undoLast() {
        val service = AgentAccessibilityService.instance ?: return
        val plan = AgentSession.lastUndoStack?.rewindPlan().orEmpty()
        if (plan.isEmpty()) return
        executor.execute {
            var step = 0
            for (perform in plan) {
                val outcome = service.act(perform.inverse)
                AgentSession.emit(
                    NarrationEvent.Execute(Correlation("undo", step), System.currentTimeMillis(), "undo: ${perform.description}", outcome.ok),
                )
                step++
                try { Thread.sleep(800) } catch (_: InterruptedException) {}
                if (!outcome.ok) break
            }
            AgentSession.emit(NarrationEvent.Done(Correlation("undo", step), System.currentTimeMillis(), "Undid $step step(s)"))
        }
    }

    private fun toRecord(intent: String, result: LoopResult): TaskRecord = when (result) {
        is LoopResult.Completed -> TaskRecord(intent, TaskStatus.COMPLETED, result.summary, result.steps, System.currentTimeMillis())
        is LoopResult.Aborted -> TaskRecord(intent, TaskStatus.ABORTED, result.reason, result.steps, System.currentTimeMillis())
    }

    /** Launchable apps (label -> package) so the planner can open one directly. */
    private fun installedApps(service: AgentAccessibilityService): List<AppInfo> {
        val pm = service.packageManager
        val launchers = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launchers, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == service.packageName) return@mapNotNull null // hide ourselves
                AppInfo(ri.loadLabel(pm).toString(), pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

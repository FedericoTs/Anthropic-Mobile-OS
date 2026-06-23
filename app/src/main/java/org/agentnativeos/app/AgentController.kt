package org.agentnativeos.app

import android.content.Intent
import android.util.Log
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.action.AgentAction
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
            val loop = AgentLoop(
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
                availableApps = installedApps(service),
                // Direct fast paths, filtered to what THIS device can actually handle.
                capabilities = DeviceCapabilities.discover(service),
                // What the agent has done before — continuity + "what have you done?".
                recentTasks = memory.recent(8),
                cancelled = { AgentSession.stopRequested },
                undo = undo,
            )
            val result = loop.run(intent)
            memory.record(toRecord(intent, result))
            Log.i(TAG, "result: $result")
            AgentSession.finish(result)
            onResult(result)
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

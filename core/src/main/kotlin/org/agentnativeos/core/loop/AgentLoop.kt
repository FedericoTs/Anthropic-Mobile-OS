package org.agentnativeos.core.loop

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyDecision
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.action.UntrustedObservation
import org.agentnativeos.core.action.label
import org.agentnativeos.core.events.Correlation
import org.agentnativeos.core.events.EventBus
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.model.AppInfo
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.PlanningContext
import org.agentnativeos.core.perception.NodeFinder
import org.agentnativeos.core.perception.Observation

/** Device-facing seams the loop drives. Real impls live in the Android app. */
fun interface Perceiver {
    fun perceive(): Observation
}

data class ActionOutcome(val ok: Boolean, val detail: String = "")

fun interface Actuator {
    fun act(action: AgentAction): ActionOutcome
}

/** Approves/denies a high-side-effect action (the UI on device; a fake in tests). */
fun interface ConfirmationHandler {
    fun confirm(action: AgentAction, reason: String): Boolean
}

sealed interface LoopResult {
    data class Completed(val summary: String, val steps: Int) : LoopResult
    data class Aborted(val reason: String, val gotAsFar: String, val steps: Int) : LoopResult
}

/**
 * The per-step agent loop for Milestone 0:
 *
 *   perceive -> plan (model, with the screen fenced as UNTRUSTED data) ->
 *   out-of-model policy gate (high side-effect => confirm) -> settle (re-perceive
 *   and verify the target still resolves) -> act -> narrate.
 *
 * On any action failure or a settle mismatch it performs T3: abort + transparent
 * handoff ("got as far as X"), leaving the partial state in the audit stream.
 * Per-step replanning means the model picks the next action after seeing each
 * real screen (decision 2A); latency is measured before optimizing.
 */
class AgentLoop(
    private val perceiver: Perceiver,
    private val provider: ModelProvider,
    private val actuator: Actuator,
    private val gate: PolicyGate,
    private val confirmer: ConfirmationHandler,
    private val bus: EventBus,
    private val clock: () -> Long = { 0L },
    private val maxSteps: Int = 25,
    /** Pause after each action so an async screen transition settles before the next perceive. */
    private val settleMs: Long = 0,
    private val idle: (Long) -> Unit = {},
    /** A stale target re-plans rather than aborts, up to this many times in a row. */
    private val maxStaleReplans: Int = 3,
    /** The same action executed this many times in a row with no progress => stop (stuck). */
    private val maxStuckRepeats: Int = 3,
    /** A failed action re-plans (with the error fed back) rather than aborts, up to this many times. */
    private val maxActionRetries: Int = 3,
    /** Apps the planner may launch directly by package (perceived from the device). */
    private val availableApps: List<AppInfo> = emptyList(),
    private val cancelled: () -> Boolean = { false },
    private val undo: org.agentnativeos.core.undo.UndoStack? = null,
    private val compensationPlanner: org.agentnativeos.core.undo.CompensationPlanner =
        org.agentnativeos.core.undo.CompensationPlanner(gate),
) {
    fun run(intent: String, taskId: String = "t1"): LoopResult {
        val history = mutableListOf<AgentAction>()
        var gotAsFar = "nothing yet"
        var staleStreak = 0
        var lastActedLabel: String? = null
        var actedRepeats = 0
        var lastError: String? = null
        var failStreak = 0

        for (step in 0 until maxSteps) {
            val corr = Correlation(taskId, step)

            // 0) cooperative cancellation (the Stop control) — abort between steps
            if (cancelled()) {
                val reason = "stopped by you"
                bus.emit(NarrationEvent.Failure(corr, clock(), reason, gotAsFar))
                return LoopResult.Aborted(reason, gotAsFar, step)
            }

            // 1) perceive
            val observation = perceiver.perceive()
            bus.emit(
                NarrationEvent.Perceive(
                    corr, clock(),
                    NodeFinder.nodeCount(observation.root),
                    observation.rootPackage,
                ),
            )

            // 2) plan — the screen goes in as untrusted data, never instructions
            bus.emit(NarrationEvent.Plan(corr, clock(), intent))
            val action = provider.nextAction(
                PlanningContext(
                    intent = intent,
                    untrustedScreen = UntrustedObservation.wrap(NodeFinder.describe(observation.root)),
                    stepIndex = step,
                    history = history.toList(),
                    availableApps = availableApps,
                    lastError = lastError,
                ),
            )

            // terminal actions
            when (action) {
                is AgentAction.Done -> {
                    bus.emit(NarrationEvent.Done(corr, clock(), action.summary))
                    return LoopResult.Completed(action.summary, step)
                }
                is AgentAction.Abort -> {
                    bus.emit(NarrationEvent.Failure(corr, clock(), action.reason, gotAsFar))
                    return LoopResult.Aborted(action.reason, gotAsFar, step)
                }
                else -> Unit
            }

            // 3) out-of-model policy gate
            val decision = gate.decide(action)
            bus.emit(
                NarrationEvent.Propose(
                    corr, clock(), action.label(),
                    highSideEffect = decision is PolicyDecision.RequireConfirm,
                ),
            )
            when (decision) {
                is PolicyDecision.Block -> {
                    bus.emit(NarrationEvent.Failure(corr, clock(), decision.reason, gotAsFar))
                    return LoopResult.Aborted(decision.reason, gotAsFar, step)
                }
                is PolicyDecision.RequireConfirm -> {
                    val approved = confirmer.confirm(action, decision.reason)
                    bus.emit(NarrationEvent.Confirm(corr, clock(), action.label(), approved))
                    if (!approved) {
                        val reason = "you skipped ${action.label()}"
                        bus.emit(NarrationEvent.Failure(corr, clock(), reason, gotAsFar))
                        return LoopResult.Aborted(reason, gotAsFar, step)
                    }
                }
                PolicyDecision.Allow -> Unit
            }

            // 4) settling protocol — re-read the tree, verify the target survives.
            //    The fresh observation also lets us capture prior state for undo.
            val settled = settle(action)
            if (settled == null) {
                // Never act on a stale target. Rather than give up on a transient
                // screen change (e.g. an async navigation still animating), let the
                // screen settle and re-plan against the fresh tree — up to a cap.
                if (staleStreak < maxStaleReplans) {
                    staleStreak++
                    if (settleMs > 0) idle(settleMs)
                    continue
                }
                val reason = "the screen changed before acting (stale target)"
                bus.emit(NarrationEvent.Failure(corr, clock(), reason, gotAsFar))
                return LoopResult.Aborted(reason, gotAsFar, step)
            }

            // 5) act
            val outcome = actuator.act(action)
            bus.emit(NarrationEvent.Execute(corr, clock(), action.label(), outcome.ok))
            if (!outcome.ok) {
                val reason = buildString {
                    append("couldn't ").append(action.label())
                    if (outcome.detail.isNotEmpty()) append(" (").append(outcome.detail).append(')')
                }
                bus.emit(NarrationEvent.Failure(corr, clock(), reason, gotAsFar))
                // Don't give up on the first miss: feed the error back and let the
                // model try a different approach (e.g. tap digit keys instead of
                // typing into a timer), up to a cap, then hand off.
                if (failStreak < maxActionRetries) {
                    failStreak++
                    lastError = reason
                    if (settleMs > 0) idle(settleMs)
                    continue
                }
                return LoopResult.Aborted(reason, gotAsFar, step)
            }
            staleStreak = 0
            failStreak = 0
            lastError = null

            // 5b) stuck detection — the same action acted repeatedly without the
            //     screen progressing means we're in a loop (e.g. tapping Cancel on a
            //     dialog that keeps reappearing). Stop and hand off rather than spin.
            if (action.label() == lastActedLabel) {
                actedRepeats++
                if (actedRepeats >= maxStuckRepeats) {
                    val reason = "stuck repeating ${action.label()} without progress"
                    bus.emit(NarrationEvent.Failure(corr, clock(), reason, gotAsFar))
                    return LoopResult.Aborted(reason, gotAsFar, step)
                }
            } else {
                actedRepeats = 1
                lastActedLabel = action.label()
            }

            // 6) record how to undo this step (E2-4 compensation model)
            undo?.record(action, compensationPlanner.compensationFor(action, priorText(action, settled)))

            history.add(action)
            gotAsFar = action.label()

            // 7) let an async screen transition settle before the next perceive.
            if (settleMs > 0) idle(settleMs)
        }
        return LoopResult.Aborted("step budget exhausted", gotAsFar, maxSteps)
    }

    /** Re-read the live tree; return the fresh observation if the target still
     *  resolves (or there's no targeted element), else null to abort. */
    private fun settle(action: AgentAction): Observation? {
        val fresh = perceiver.perceive()
        val query = targetQuery(action) ?: return fresh
        return if (NodeFinder.find(fresh.root, query) != null) fresh else null
    }

    private fun targetQuery(action: AgentAction): String? = when (action) {
        is AgentAction.Tap -> action.targetQuery
        is AgentAction.TypeText -> action.targetQuery
        else -> null
    }

    private fun priorText(action: AgentAction, observation: Observation): String? =
        (action as? AgentAction.TypeText)?.let {
            NodeFinder.find(observation.root, it.targetQuery)?.text
        }
}

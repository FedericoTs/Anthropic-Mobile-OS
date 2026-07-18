package org.agentnativeos.core.multiagent

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.UntrustedObservation
import org.agentnativeos.core.action.label
import org.agentnativeos.core.events.Correlation
import org.agentnativeos.core.events.EventBus
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.model.AppInfo
import org.agentnativeos.core.model.Capability
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.PlanningContext
import org.agentnativeos.core.perception.NodeFinder
import org.agentnativeos.core.perception.Observation

/**
 * A model-backed [SubAgent]: one goal, planned step-by-step by the provider against the
 * screen the coordinator hands it. Carries the single-agent loop's honesty guarantees
 * into multi-agent runs: a claimed "done" is VERIFIED against the live screen (a rejected
 * claim re-plans immediately with the reason fed back; repeated unverifiable claims fail
 * the goal honestly), action failures feed back as lastError, a step budget bounds cost,
 * and cancellation stops it between plans. Narrates with its own agentId so the feed can
 * attribute every line. Returning null from [planNext] ALWAYS means finished (the
 * coordinator's contract) — [finishedOk]/[failed]/[outcome] carry how it ended.
 */
class ModelSubAgent(
    override val id: String,
    val goal: String,
    private val provider: ModelProvider,
    private val availableApps: List<AppInfo> = emptyList(),
    private val capabilities: List<Capability> = emptyList(),
    private val userInterests: List<String> = emptyList(),
    private val bus: EventBus? = null,
    private val taskId: String = "multi",
    private val clock: () -> Long = { 0L },
    private val maxSteps: Int = 12,
    private val maxDoneVerifyFails: Int = 2,
    private val cancelled: () -> Boolean = { false },
) : SubAgent {

    var outcome: String? = null
        private set
    var failed = false
        private set
    val finishedOk: Boolean get() = finished && !failed

    private var finished = false
    private var step = 0
    private var lastError: String? = null
    private var doneVerifyFails = 0
    private val history = mutableListOf<AgentAction>()

    override fun planNext(observation: Observation): AgentAction? {
        if (finished) return null
        if (cancelled()) return finish(ok = false, "stopped by you")

        while (step < maxSteps) {
            val corr = Correlation(taskId, step, id)
            val screen = NodeFinder.describe(observation.root)
            bus?.emit(NarrationEvent.Plan(corr, clock(), "[$id] $goal"))
            val context = PlanningContext(
                intent = goal,
                untrustedScreen = UntrustedObservation.wrap(screen),
                stepIndex = step,
                history = history.toList(),
                availableApps = availableApps,
                capabilities = capabilities,
                userInterests = userInterests,
                lastError = lastError,
            )
            step++
            when (val action = provider.nextAction(context)) {
                is AgentAction.Done -> {
                    // Same honest-completion policy as the single-agent loop.
                    val verdict = provider.verify(context, action.summary)
                    bus?.emit(NarrationEvent.Verify(corr, clock(), verdict.verified, verdict.reason))
                    if (verdict.verified) return finish(ok = true, action.summary, action.places)
                    doneVerifyFails++
                    if (doneVerifyFails >= maxDoneVerifyFails) {
                        return finish(ok = false, "reported done but could not verify: ${verdict.reason}")
                    }
                    // Not actually done — feed the reason back and re-plan right away.
                    lastError = "You reported this goal done, but the screen says it is NOT " +
                        "complete: ${verdict.reason}. Keep going and actually finish it."
                }
                is AgentAction.Abort -> return finish(ok = false, action.reason)
                else -> {
                    bus?.emit(NarrationEvent.Propose(corr, clock(), "[$id] ${action.label()}", highSideEffect = false))
                    return action
                }
            }
        }
        return finish(ok = false, "step budget exhausted")
    }

    override fun onResult(action: AgentAction, ok: Boolean) {
        if (ok) {
            history.add(action)
            lastError = null
        } else {
            lastError = "couldn't ${action.label()} — try a different element or approach"
        }
    }

    private fun finish(
        ok: Boolean,
        summary: String,
        places: List<org.agentnativeos.core.action.Place> = emptyList(),
    ): AgentAction? {
        finished = true
        failed = !ok
        outcome = summary
        val corr = Correlation(taskId, step, id)
        bus?.emit(
            if (ok) {
                NarrationEvent.Done(corr, clock(), "[$id] $summary", places)
            } else {
                NarrationEvent.Failure(
                    corr, clock(), "[$id] $summary",
                    gotAsFar = history.lastOrNull()?.label() ?: "nothing yet",
                )
            },
        )
        return null
    }
}

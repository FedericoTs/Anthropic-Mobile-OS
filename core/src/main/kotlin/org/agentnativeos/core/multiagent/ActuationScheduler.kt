package org.agentnativeos.core.multiagent

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.action.SideEffect
import org.agentnativeos.core.action.label
import org.agentnativeos.core.action.uiTarget
import org.agentnativeos.core.loop.Actuator
import org.agentnativeos.core.loop.Perceiver
import org.agentnativeos.core.perception.NodeFinder
import org.agentnativeos.core.perception.Observation

data class ScheduledAction(val agentId: String, val action: AgentAction)

data class ReviewItem(val agentId: String, val action: AgentAction, val reason: String)

/** One consolidated review for all high-side-effect actions queued in a batch. */
data class ConsolidatedReview(val items: List<ReviewItem>) {
    /**
     * Two high-side-effect actions fighting over the same UI target can't both be
     * committed safely — block and ask rather than racing them.
     */
    val isConflict: Boolean
        get() {
            val targets = items.mapNotNull { it.action.uiTarget() }
            return targets.size != targets.distinct().size
        }
}

fun interface BatchConfirm {
    /** Called ONCE per batch with all high-side-effect items; returns approved agent ids. */
    fun confirm(review: ConsolidatedReview): Set<String>
}

enum class ActuationStatus { ACTUATED, REPLANNED_AND_ACTUATED, SKIPPED_STALE, BLOCKED_CONFLICT, DENIED, FAILED }

data class ActuationOutcome(
    val agentId: String,
    val action: AgentAction,
    val status: ActuationStatus,
    val detail: String = "",
)

/**
 * E2-3 (1A): parallel planning, SERIALIZED actuation. Sub-agents reason in
 * parallel, but this scheduler owns the one foreground screen and actuates one
 * action at a time. At dequeue it re-validates each action against the live tree
 * and RE-PLANS (not just aborts) on a stale target — serializing taps does not
 * serialize the world the taps assumed. High-side-effect actions across
 * sub-agents are consolidated into ONE confirm; conflicting ones (same target)
 * block and ask.
 */
class ActuationScheduler(
    private val perceiver: Perceiver,
    private val actuator: Actuator,
    private val gate: PolicyGate = PolicyGate(),
) {
    fun runBatch(
        batch: List<ScheduledAction>,
        replan: (agentId: String, observation: Observation) -> AgentAction?,
        confirm: BatchConfirm,
    ): List<ActuationOutcome> {
        val highItems = batch
            .filter { gate.classify(it.action) == SideEffect.IRREVERSIBLE }
            .map { ReviewItem(it.agentId, it.action, "high side-effect: ${it.action.label()}") }
        val review = ConsolidatedReview(highItems)

        val blockedByConflict = review.isConflict
        val approved: Set<String> = when {
            blockedByConflict -> emptySet()
            highItems.isEmpty() -> emptySet()
            else -> confirm.confirm(review) // ONE consolidated review for the whole batch
        }

        val outcomes = mutableListOf<ActuationOutcome>()
        for (item in batch) {
            var action = item.action
            var replanned = false

            // Re-validate at dequeue; re-plan (not abort) if the target went stale.
            if (!resolves(perceiver.perceive(), action)) {
                val fresh = perceiver.perceive()
                val newAction = replan(item.agentId, fresh)
                if (newAction == null || !resolves(perceiver.perceive(), newAction)) {
                    outcomes += ActuationOutcome(item.agentId, action, ActuationStatus.SKIPPED_STALE)
                    continue
                }
                action = newAction
                replanned = true
            }

            if (gate.classify(action) == SideEffect.IRREVERSIBLE) {
                if (blockedByConflict) {
                    outcomes += ActuationOutcome(item.agentId, action, ActuationStatus.BLOCKED_CONFLICT)
                    continue
                }
                if (item.agentId !in approved) {
                    outcomes += ActuationOutcome(item.agentId, action, ActuationStatus.DENIED)
                    continue
                }
            }

            val outcome = actuator.act(action)
            val status = when {
                !outcome.ok -> ActuationStatus.FAILED
                replanned -> ActuationStatus.REPLANNED_AND_ACTUATED
                else -> ActuationStatus.ACTUATED
            }
            outcomes += ActuationOutcome(item.agentId, action, status, outcome.detail)
        }
        return outcomes
    }

    private fun resolves(observation: Observation, action: AgentAction): Boolean {
        val query = action.uiTarget() ?: return true
        return NodeFinder.find(observation.root, query) != null
    }
}

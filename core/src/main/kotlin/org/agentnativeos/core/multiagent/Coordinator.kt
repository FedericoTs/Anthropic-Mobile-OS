package org.agentnativeos.core.multiagent

import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.loop.Actuator
import org.agentnativeos.core.loop.Perceiver

/** Merged result of a multi-agent run (partial results are first-class). */
data class CoordinatorResult(
    val completed: List<String>,
    val unfinished: List<String>,
    val actuations: List<ActuationOutcome>,
)

/**
 * Runs N sub-agents to completion. Each round they plan in parallel against the
 * current screen, then a single [ActuationScheduler] serializes their actions on
 * the one foreground screen (re-validating + re-planning stale taps). Round-robin
 * ordering keeps any one sub-agent from starving the device queue; partial
 * results (some done, some not) are returned day-one rather than bolted on later.
 */
class Coordinator(
    private val perceiver: Perceiver,
    private val actuator: Actuator,
    private val gate: PolicyGate = PolicyGate(),
    private val maxRounds: Int = 50,
) {
    private val scheduler = ActuationScheduler(perceiver, actuator, gate)

    fun run(
        subAgents: List<SubAgent>,
        confirm: BatchConfirm = BatchConfirm { emptySet() },
    ): CoordinatorResult {
        val active = LinkedHashMap<String, SubAgent>().apply { subAgents.forEach { put(it.id, it) } }
        val completed = mutableListOf<String>()
        val actuations = mutableListOf<ActuationOutcome>()
        var round = 0

        while (active.isNotEmpty() && round < maxRounds) {
            val observation = perceiver.perceive()
            val batch = mutableListOf<ScheduledAction>()

            // Parallel planning (synchronous here): each active sub-agent proposes.
            for (agent in fairOrder(active.values.toList(), round)) {
                val action = agent.planNext(observation)
                if (action == null) completed += agent.id else batch += ScheduledAction(agent.id, action)
            }
            completed.forEach { active.remove(it) }

            if (batch.isNotEmpty()) {
                val outs = scheduler.runBatch(
                    batch = batch,
                    replan = { id, obs -> active[id]?.planNext(obs) },
                    confirm = confirm,
                )
                actuations += outs
                outs.forEach { o ->
                    val ok = o.status == ActuationStatus.ACTUATED ||
                        o.status == ActuationStatus.REPLANNED_AND_ACTUATED
                    active[o.agentId]?.onResult(o.action, ok)
                }
            }
            round++
        }
        return CoordinatorResult(completed.distinct(), active.keys.toList(), actuations)
    }

    /** Round-robin rotation so no sub-agent is always first/last — fairness. */
    private fun fairOrder(agents: List<SubAgent>, round: Int): List<SubAgent> {
        if (agents.isEmpty()) return agents
        val shift = round % agents.size
        return agents.drop(shift) + agents.take(shift)
    }
}

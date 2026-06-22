package org.agentnativeos.core.multiagent

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.perception.Observation

/**
 * A reasoning sub-agent. Sub-agents plan in parallel with their peers; a single
 * device scheduler serializes the actions they produce against the one foreground
 * screen.
 */
interface SubAgent {
    val id: String

    /** The next action given the live screen, or null when this sub-agent is done. */
    fun planNext(observation: Observation): AgentAction?

    /** Optional feedback after the coordinator actuated (or didn't). */
    fun onResult(action: AgentAction, ok: Boolean) {}
}

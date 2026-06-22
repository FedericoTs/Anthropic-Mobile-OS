package org.agentnativeos.core.multiagent

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.perception.Observation
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class ScriptedSubAgent(override val id: String, private val plan: List<AgentAction?>) : SubAgent {
    private var i = 0
    override fun planNext(observation: Observation): AgentAction? =
        if (i < plan.size) plan[i++] else null
}

/** Always proposes the same action (used to model a sub-agent that can't progress). */
private class LoopingSubAgent(override val id: String, private val action: AgentAction) : SubAgent {
    override fun planNext(observation: Observation): AgentAction? = action
}

class CoordinatorTest {

    @Test
    fun runsTwoSubAgentsToCompletion() {
        val actuator = RecordingActuator(succeed = true)
        val result = Coordinator(StaticPerceiver(screenWith("A", "B")), actuator).run(
            listOf(
                ScriptedSubAgent("a1", listOf(AgentAction.Tap("A"), null)),
                ScriptedSubAgent("a2", listOf(AgentAction.Tap("B"), null)),
            ),
        )
        assertTrue(result.completed.containsAll(listOf("a1", "a2")))
        assertTrue(result.unfinished.isEmpty())
        assertEquals(2, result.actuations.size)
    }

    @Test
    fun aStuckSubAgentDoesNotStarveTheOthers() {
        val actuator = RecordingActuator(succeed = true)
        val result = Coordinator(StaticPerceiver(screenWith("B")), actuator, maxRounds = 5).run(
            listOf(
                LoopingSubAgent("stuck", AgentAction.Tap("GONE")), // target never present
                ScriptedSubAgent("a2", listOf(AgentAction.Tap("B"), null)),
            ),
        )
        assertTrue("the healthy sub-agent still completes", "a2" in result.completed)
        assertTrue("the stuck one is reported unfinished", "stuck" in result.unfinished)
    }

    @Test
    fun returnsPartialResults() {
        val actuator = RecordingActuator(succeed = true)
        val result = Coordinator(StaticPerceiver(screenWith("A")), actuator, maxRounds = 3).run(
            listOf(
                ScriptedSubAgent("done", listOf(AgentAction.Tap("A"), null)),
                LoopingSubAgent("never", AgentAction.Tap("GONE")),
            ),
        )
        assertEquals(listOf("done"), result.completed)
        assertEquals(listOf("never"), result.unfinished)
    }
}

package org.agentnativeos.core.multiagent

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.events.AuditLog
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.PlanningContext
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.model.VerifyResult
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSubAgentTest {

    private val screen = screenWith("Battery", "Wi-Fi").root!!
    private fun obs() = org.agentnativeos.core.perception.Observation("com.test", screen, 0L)

    @Test
    fun plansUntilDoneThenReportsOutcome_nullAlwaysMeansFinished() {
        val agent = ModelSubAgent(
            id = "g1", goal = "open battery",
            provider = ScriptedModelProvider(listOf(AgentAction.Tap("Battery"), AgentAction.Done("opened"))),
        )
        val first = agent.planNext(obs())
        assertEquals(AgentAction.Tap("Battery"), first)
        agent.onResult(first!!, ok = true)

        assertNull("Done -> finished", agent.planNext(obs()))
        assertTrue(agent.finishedOk)
        assertEquals("opened", agent.outcome)
        assertNull("stays finished", agent.planNext(obs()))
    }

    @Test
    fun abortMarksTheGoalFailedWithItsReason() {
        val agent = ModelSubAgent(
            id = "g1", goal = "impossible",
            provider = ScriptedModelProvider(listOf(AgentAction.Abort("can't find it"))),
        )
        assertNull(agent.planNext(obs()))
        assertTrue(agent.failed)
        assertFalse(agent.finishedOk)
        assertEquals("can't find it", agent.outcome)
    }

    @Test
    fun rejectedDoneClaimReplansImmediatelyWithTheReasonFedBack() {
        val seenErrors = mutableListOf<String?>()
        val provider = object : ModelProvider {
            private var calls = 0
            override fun nextAction(context: PlanningContext): AgentAction {
                seenErrors.add(context.lastError)
                return when (calls++) {
                    0 -> AgentAction.Done("sent") // premature — verifier rejects
                    1 -> AgentAction.Tap("Battery") // re-plan in the SAME planNext call
                    else -> AgentAction.Done("actually sent")
                }
            }
            private var checks = 0
            override fun verify(context: PlanningContext, claimedSummary: String) =
                if (checks++ == 0) VerifyResult(false, "still in compose") else VerifyResult(true)
        }
        val agent = ModelSubAgent(id = "g1", goal = "send it", provider = provider)

        // One planNext survives the rejected claim and returns the re-planned action.
        assertEquals(AgentAction.Tap("Battery"), agent.planNext(obs()))
        assertTrue(seenErrors.any { it?.contains("NOT complete") == true })

        agent.onResult(AgentAction.Tap("Battery"), ok = true)
        assertNull(agent.planNext(obs()))
        assertTrue(agent.finishedOk)
    }

    @Test
    fun repeatedUnverifiableDoneFailsTheGoalHonestly() {
        val provider = object : ModelProvider {
            override fun nextAction(context: PlanningContext) = AgentAction.Done("sent")
            override fun verify(context: PlanningContext, claimedSummary: String) =
                VerifyResult(false, "never actually sent")
        }
        val agent = ModelSubAgent(id = "g1", goal = "send it", provider = provider)
        assertNull(agent.planNext(obs()))
        assertTrue(agent.failed)
        assertTrue(agent.outcome!!.contains("could not verify"))
    }

    @Test
    fun failedActuationFeedsBackAsLastError() {
        val seenErrors = mutableListOf<String?>()
        val provider = object : ModelProvider {
            private var calls = 0
            override fun nextAction(context: PlanningContext): AgentAction {
                seenErrors.add(context.lastError)
                return if (calls++ == 0) AgentAction.Tap("Battery") else AgentAction.Done("ok")
            }
        }
        val agent = ModelSubAgent(id = "g1", goal = "g", provider = provider)
        val action = agent.planNext(obs())!!
        agent.onResult(action, ok = false)
        agent.planNext(obs())
        assertTrue(seenErrors.any { it?.contains("couldn't") == true })
    }

    @Test
    fun cancellationFinishesWithoutPlanning() {
        var planned = 0
        val provider = ModelProvider { planned++; AgentAction.Tap("Battery") }
        val agent = ModelSubAgent(id = "g1", goal = "g", provider = provider, cancelled = { true })
        assertNull(agent.planNext(obs()))
        assertTrue(agent.failed)
        assertEquals(0, planned)
    }

    @Test
    fun twoModelSubAgentsRunToCompletionThroughTheRealCoordinator() {
        // End-to-end with fakes: parallel planning, serialized actuation, both goals done.
        val bus = InMemoryEventBus()
        val audit = AuditLog().also { bus.subscribe(it) }
        val a = ModelSubAgent(
            id = "g1", goal = "open battery", bus = bus,
            provider = ScriptedModelProvider(listOf(AgentAction.Tap("Battery"), AgentAction.Done("battery open"))),
        )
        val b = ModelSubAgent(
            id = "g2", goal = "open wifi", bus = bus,
            provider = ScriptedModelProvider(listOf(AgentAction.Tap("Wi-Fi"), AgentAction.Done("wifi open"))),
        )
        val actuator = RecordingActuator(succeed = true)
        val result = Coordinator(StaticPerceiver(obs()), actuator).run(listOf(a, b))

        assertEquals(setOf("g1", "g2"), result.completed.toSet())
        assertTrue(result.unfinished.isEmpty())
        assertTrue(a.finishedOk && b.finishedOk)
        assertEquals(
            setOf(AgentAction.Tap("Battery"), AgentAction.Tap("Wi-Fi")),
            actuator.performed.toSet(),
        )
        // Narration attributed per agent.
        assertTrue(audit.entries().any { it is NarrationEvent.Done && it.live().contains("[g1]") })
        assertTrue(audit.entries().any { it is NarrationEvent.Done && it.live().contains("[g2]") })
    }
}

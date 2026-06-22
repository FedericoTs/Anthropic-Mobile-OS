package org.agentnativeos.core.multiagent

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.loop.Perceiver
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActuationSchedulerTest {

    private val approveNone = BatchConfirm { emptySet() }

    private fun scheduler(perceiver: Perceiver, actuator: RecordingActuator) =
        ActuationScheduler(perceiver, actuator)

    @Test
    fun serializesActuationInFifoOrder() {
        val actuator = RecordingActuator(succeed = true)
        val outs = scheduler(StaticPerceiver(screenWith("A", "B", "C")), actuator).runBatch(
            batch = listOf(
                ScheduledAction("a1", AgentAction.Tap("A")),
                ScheduledAction("a2", AgentAction.Tap("B")),
                ScheduledAction("a3", AgentAction.Tap("C")),
            ),
            replan = { _, _ -> null },
            confirm = approveNone,
        )
        assertEquals(
            listOf(AgentAction.Tap("A"), AgentAction.Tap("B"), AgentAction.Tap("C")),
            actuator.performed,
        )
        assertTrue(outs.all { it.status == ActuationStatus.ACTUATED })
    }

    @Test
    fun revalidatesAndReplansOnStaleTarget() {
        val actuator = RecordingActuator(succeed = true)
        // Screen has B but not A; the queued Tap("A") is stale -> re-plan to Tap("B").
        val outs = scheduler(StaticPerceiver(screenWith("B")), actuator).runBatch(
            batch = listOf(ScheduledAction("a1", AgentAction.Tap("A"))),
            replan = { _, _ -> AgentAction.Tap("B") },
            confirm = approveNone,
        )
        assertEquals(listOf(AgentAction.Tap("B")), actuator.performed)
        assertEquals(ActuationStatus.REPLANNED_AND_ACTUATED, outs.single().status)
    }

    @Test
    fun skipsWhenReplanCannotRecover() {
        val actuator = RecordingActuator(succeed = true)
        val outs = scheduler(StaticPerceiver(screenWith("B")), actuator).runBatch(
            batch = listOf(ScheduledAction("a1", AgentAction.Tap("A"))),
            replan = { _, _ -> null },
            confirm = approveNone,
        )
        assertTrue(actuator.performed.isEmpty())
        assertEquals(ActuationStatus.SKIPPED_STALE, outs.single().status)
    }

    @Test
    fun consolidatesConfirmForDistinctHighSideEffectActions() {
        val actuator = RecordingActuator(succeed = true)
        var confirmCalls = 0
        var itemsSeen = 0
        val confirm = BatchConfirm { review ->
            confirmCalls++
            itemsSeen = review.items.size
            setOf("a1") // approve only a1
        }
        val outs = scheduler(StaticPerceiver(screenWith("Send email", "Pay invoice")), actuator).runBatch(
            batch = listOf(
                ScheduledAction("a1", AgentAction.Tap("Send email")),
                ScheduledAction("a2", AgentAction.Tap("Pay invoice")),
            ),
            replan = { _, _ -> null },
            confirm = confirm,
        )
        assertEquals("one consolidated confirm for the batch", 1, confirmCalls)
        assertEquals(2, itemsSeen)
        assertEquals(listOf(AgentAction.Tap("Send email")), actuator.performed)
        assertEquals(ActuationStatus.DENIED, outs.first { it.agentId == "a2" }.status)
    }

    @Test
    fun blocksConflictingHighSideEffectOnSameTarget() {
        val actuator = RecordingActuator(succeed = true)
        var confirmCalls = 0
        val confirm = BatchConfirm { confirmCalls++; setOf("a1", "a2") }
        val outs = scheduler(StaticPerceiver(screenWith("Send")), actuator).runBatch(
            batch = listOf(
                ScheduledAction("a1", AgentAction.Tap("Send")),
                ScheduledAction("a2", AgentAction.Tap("Send")),
            ),
            replan = { _, _ -> null },
            confirm = confirm,
        )
        assertEquals("conflicts are blocked, never auto-confirmed", 0, confirmCalls)
        assertTrue(actuator.performed.isEmpty())
        assertTrue(outs.all { it.status == ActuationStatus.BLOCKED_CONFLICT })
    }
}

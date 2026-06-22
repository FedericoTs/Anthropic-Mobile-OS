package org.agentnativeos.core.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventBusTest {

    private fun perceive(step: Int, n: Int) =
        NarrationEvent.Perceive(Correlation("t", step), 0L, n, "com.test")

    @Test
    fun auditKeepsEverything_liveCoalescesAndBounds() {
        val bus = InMemoryEventBus()
        val audit = AuditLog()
        val live = LiveFeed(capacity = 3)
        bus.subscribe(audit)
        bus.subscribe(live)

        // Three perceive ticks for the SAME step should coalesce in the live feed.
        bus.emit(perceive(0, 10))
        bus.emit(perceive(0, 11))
        bus.emit(perceive(0, 12))
        bus.emit(NarrationEvent.Done(Correlation("t", 0), 0L, "ok"))

        // Audit keeps all four.
        assertEquals(4, audit.entries().size)
        // Live coalesced the three same-step perceives into one, plus Done => 2.
        assertEquals(2, live.snapshot().size)
        assertTrue(live.lines().last().startsWith("Done"))
    }

    @Test
    fun liveFeedRespectsCapacity() {
        val bus = InMemoryEventBus()
        val live = LiveFeed(capacity = 2)
        bus.subscribe(live)
        repeat(5) { bus.emit(NarrationEvent.Plan(Correlation("t", it), 0L, "intent $it")) }
        assertEquals(2, live.snapshot().size)
        // Only the last two survive.
        assertTrue(live.lines().any { it.contains("intent 4") })
        assertTrue(live.lines().any { it.contains("intent 3") })
    }

    @Test
    fun correlationIsCarriedOnEvents() {
        val e = NarrationEvent.Execute(Correlation("task-9", 4, "agent-b"), 0L, "tap x", true)
        assertEquals("task-9", e.correlation.taskId)
        assertEquals(4, e.correlation.stepId)
        assertEquals("agent-b", e.correlation.agentId)
    }
}

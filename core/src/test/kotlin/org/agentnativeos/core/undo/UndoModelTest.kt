package org.agentnativeos.core.undo

import org.agentnativeos.core.action.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoModelTest {

    private val planner = CompensationPlanner()

    // --- CompensationPlanner ---

    @Test
    fun typeText_isUndoableByRestoringPriorText() {
        val c = planner.compensationFor(AgentAction.TypeText("note", "new"), priorText = "old")
        assertTrue(c is Compensation.Undoable)
        assertEquals(AgentAction.TypeText("note", "old"), (c as Compensation.Undoable).inverse)
    }

    @Test
    fun navigation_isUndoableByGoingBack() {
        assertEquals(
            AgentAction.Back,
            (planner.compensationFor(AgentAction.Tap("Settings")) as Compensation.Undoable).inverse,
        )
        assertEquals(
            AgentAction.Back,
            (planner.compensationFor(AgentAction.LaunchApp("com.x")) as Compensation.Undoable).inverse,
        )
    }

    @Test
    fun highSideEffect_isIrreversible() {
        assertTrue(planner.compensationFor(AgentAction.Tap("Send money")) is Compensation.Irreversible)
        assertTrue(planner.compensationFor(AgentAction.Tap("Confirm payment")) is Compensation.Irreversible)
    }

    @Test
    fun terminalActions_haveNoCompensation() {
        assertEquals(Compensation.None, planner.compensationFor(AgentAction.Done("ok")))
        assertEquals(Compensation.None, planner.compensationFor(AgentAction.Abort("x")))
    }

    // --- UndoStack ---

    @Test
    fun undoesReversibleStepsInReverseOrder() {
        val stack = UndoStack()
        stack.record(AgentAction.Tap("Network"), planner.compensationFor(AgentAction.Tap("Network")))
        stack.record(AgentAction.TypeText("Search", "wifi"), planner.compensationFor(AgentAction.TypeText("Search", "wifi"), "old"))
        assertEquals(2, stack.depth())
        assertTrue(stack.canUndo)

        val first = stack.undoLast()
        assertTrue(first is UndoResult.Perform)
        assertEquals(AgentAction.TypeText("Search", "old"), (first as UndoResult.Perform).inverse)

        val second = stack.undoLast()
        assertEquals(AgentAction.Back, (second as UndoResult.Perform).inverse)

        assertEquals(UndoResult.NothingToUndo, stack.undoLast())
    }

    @Test
    fun rewindPlanReturnsAllInversesNewestFirst() {
        val stack = UndoStack()
        stack.record(AgentAction.LaunchApp("com.x"), planner.compensationFor(AgentAction.LaunchApp("com.x")))
        stack.record(AgentAction.Tap("Battery"), planner.compensationFor(AgentAction.Tap("Battery")))
        stack.record(AgentAction.TypeText("Search", "wifi"), planner.compensationFor(AgentAction.TypeText("Search", "wifi"), "old"))

        val plan = stack.rewindPlan().map { it.inverse }
        assertEquals(
            listOf(AgentAction.TypeText("Search", "old"), AgentAction.Back, AgentAction.Back),
            plan,
        )
        assertEquals(0, stack.depth())
    }

    @Test
    fun rewindPlanStopsAtAnIrreversibleBarrier() {
        val stack = UndoStack()
        stack.record(AgentAction.Tap("Compose"), planner.compensationFor(AgentAction.Tap("Compose")))
        stack.record(AgentAction.Tap("Send"), planner.compensationFor(AgentAction.Tap("Send"))) // irreversible
        stack.record(AgentAction.Tap("Back to inbox"), planner.compensationFor(AgentAction.Tap("Back to inbox")))

        // Only the step after the barrier can be undone; the sent action stays.
        assertEquals(listOf(AgentAction.Back), stack.rewindPlan().map { it.inverse })
        assertEquals(2, stack.depth())
    }

    @Test
    fun cannotRewindPastAnIrreversibleStep() {
        val stack = UndoStack()
        stack.record(AgentAction.Tap("Compose"), planner.compensationFor(AgentAction.Tap("Compose")))
        stack.record(AgentAction.Tap("Send"), planner.compensationFor(AgentAction.Tap("Send")))

        assertFalse("a sent message must not look undoable", stack.canUndo)
        val result = stack.undoLast()
        assertTrue("undo past an irreversible step is blocked", result is UndoResult.Blocked)
        // The barrier stays — depth unchanged, the earlier reversible step stays protected.
        assertEquals(2, stack.depth())
    }
}

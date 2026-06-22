package org.agentnativeos.core.loop

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.screenWith
import org.agentnativeos.core.undo.UndoResult
import org.agentnativeos.core.undo.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loop records a compensation for each executed step into the UndoStack. */
class UndoLoopTest {

    @Test
    fun loopRecordsCompensationsForEachStep() {
        val undo = UndoStack()
        val loop = AgentLoop(
            perceiver = StaticPerceiver(screenWith("Network", "Search", "Wi-Fi")),
            provider = ScriptedModelProvider(
                listOf(
                    AgentAction.Tap("Network"),
                    AgentAction.TypeText("Search", "wifi"),
                    AgentAction.Done("done"),
                ),
            ),
            actuator = RecordingActuator(succeed = true),
            gate = PolicyGate(),
            confirmer = ConfirmationHandler { _, _ -> true },
            bus = InMemoryEventBus(),
            undo = undo,
        )

        val result = loop.run("set up wifi")
        assertTrue(result is LoopResult.Completed)

        // Two reversible steps recorded (Done contributes no compensation).
        assertEquals(2, undo.depth())
        // Undo pops the type first (restore), then the tap (back).
        assertTrue(undo.undoLast() is UndoResult.Perform)
        assertEquals(AgentAction.Back, (undo.undoLast() as UndoResult.Perform).inverse)
    }
}

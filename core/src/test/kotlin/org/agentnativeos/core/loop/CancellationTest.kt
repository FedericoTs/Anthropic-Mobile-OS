package org.agentnativeos.core.loop

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationTest {

    @Test
    fun cancellationAbortsBeforeActing() {
        val actuator = RecordingActuator(succeed = true)
        // A long script we never want to finish; cancel after the first perceive.
        var ticks = 0
        val loop = AgentLoop(
            perceiver = StaticPerceiver(screenWith("Battery", "Wi-Fi")),
            provider = ScriptedModelProvider(List(10) { AgentAction.Tap("Battery") }),
            actuator = actuator,
            gate = PolicyGate(),
            confirmer = ConfirmationHandler { _, _ -> true },
            bus = InMemoryEventBus(),
            cancelled = { ticks++ >= 1 }, // allow step 0, cancel at step 1
        )
        val result = loop.run("do a lot")
        assertTrue(result is LoopResult.Aborted)
        assertEquals("stopped by you", (result as LoopResult.Aborted).reason)
        // Exactly one action ran before the stop took effect.
        assertEquals(1, actuator.performed.size)
    }

    @Test
    fun cancelledFromTheStartDoesNothing() {
        val actuator = RecordingActuator(succeed = true)
        val loop = AgentLoop(
            perceiver = StaticPerceiver(screenWith("Battery")),
            provider = ScriptedModelProvider(listOf(AgentAction.Tap("Battery"))),
            actuator = actuator,
            gate = PolicyGate(),
            confirmer = ConfirmationHandler { _, _ -> true },
            bus = InMemoryEventBus(),
            cancelled = { true },
        )
        val result = loop.run("noop")
        assertTrue(result is LoopResult.Aborted)
        assertTrue(actuator.performed.isEmpty())
    }
}

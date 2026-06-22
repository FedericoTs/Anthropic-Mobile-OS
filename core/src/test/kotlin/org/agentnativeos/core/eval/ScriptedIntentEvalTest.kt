package org.agentnativeos.core.eval

import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.loop.AgentLoop
import org.agentnativeos.core.loop.ConfirmationHandler
import org.agentnativeos.core.loop.LoopResult
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.perception.Observation
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scripted-intent eval (decision TA): a fixed set of intents with pass/fail
 * outcomes, run on every build. Each case drives the real loop with a scripted
 * planner and asserts the loop reaches the expected terminal state with the
 * expected number of actuations.
 */
class ScriptedIntentEvalTest {

    private data class Case(
        val intent: String,
        val screen: Observation,
        val script: List<AgentAction>,
        val expectedActions: Int,
    )

    private val suite = listOf(
        Case(
            "open battery settings",
            screenWith("Battery", "Network & internet", "Storage"),
            listOf(AgentAction.Tap("Battery"), AgentAction.Done("opened battery")),
            expectedActions = 1,
        ),
        Case(
            "turn on wifi",
            screenWith("Network & internet", "Wi-Fi", "Bluetooth"),
            listOf(AgentAction.Tap("Network & internet"), AgentAction.Tap("Wi-Fi"), AgentAction.Done("wifi on")),
            expectedActions = 2,
        ),
        Case(
            "search for ringtone",
            screenWith("Search settings", "Sound"),
            listOf(
                AgentAction.Tap("Search settings"),
                AgentAction.TypeText("Search settings", "ringtone"),
                AgentAction.Done("searched"),
            ),
            expectedActions = 2,
        ),
    )

    @Test
    fun scriptedIntentsAllComplete() {
        for (case in suite) {
            val actuator = RecordingActuator(succeed = true)
            val loop = AgentLoop(
                StaticPerceiver(case.screen),
                ScriptedModelProvider(case.script),
                actuator,
                PolicyGate(),
                ConfirmationHandler { _, _ -> true },
                InMemoryEventBus(),
            )
            val result = loop.run(case.intent)
            assertTrue("intent '${case.intent}' should complete, got $result", result is LoopResult.Completed)
            assertEquals(
                "intent '${case.intent}' actuation count",
                case.expectedActions,
                actuator.performed.size,
            )
        }
    }
}

package org.agentnativeos.core.loop

import org.agentnativeos.core.FakeNode
import org.agentnativeos.core.RecordingActuator
import org.agentnativeos.core.SequencePerceiver
import org.agentnativeos.core.StaticPerceiver
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.AuditLog
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.perception.Observation
import org.agentnativeos.core.screenWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {

    private val approveAll = ConfirmationHandler { _, _ -> true }
    private val denyAll = ConfirmationHandler { _, _ -> false }

    private fun loop(
        perceiver: Perceiver,
        provider: org.agentnativeos.core.model.ModelProvider,
        actuator: org.agentnativeos.core.loop.Actuator,
        confirmer: ConfirmationHandler = approveAll,
        audit: AuditLog = AuditLog(),
    ): AgentLoop {
        val bus = InMemoryEventBus().apply { subscribe(audit) }
        return AgentLoop(perceiver, provider, actuator, PolicyGate(), confirmer, bus)
    }

    @Test
    fun happyPath_perceivePlanActDone() {
        val actuator = RecordingActuator(succeed = true)
        val audit = AuditLog()
        val result = loop(
            StaticPerceiver(screenWith("Battery", "Network")),
            org.agentnativeos.core.model.ScriptedModelProvider(
                listOf(AgentAction.Tap("Battery"), AgentAction.Done("opened battery")),
            ),
            actuator, audit = audit,
        ).run("open battery")

        assertTrue(result is LoopResult.Completed)
        result as LoopResult.Completed
        assertEquals("opened battery", result.summary)
        assertEquals(1, result.steps)
        assertEquals(listOf(AgentAction.Tap("Battery")), actuator.performed)
        assertTrue(audit.entries().any { it is NarrationEvent.Execute && it.ok })
        assertTrue(audit.entries().last() is NarrationEvent.Done)
    }

    @Test
    fun actionFailure_abortsWithHandoff() {
        val actuator = RecordingActuator(succeed = false, detail = "not clickable")
        val result = loop(
            StaticPerceiver(screenWith("Battery")),
            org.agentnativeos.core.model.ScriptedModelProvider(listOf(AgentAction.Tap("Battery"))),
            actuator,
        ).run("open battery")

        assertTrue(result is LoopResult.Aborted)
        result as LoopResult.Aborted
        assertTrue(result.reason.contains("couldn't"))
        assertEquals("nothing yet", result.gotAsFar)
        assertEquals(1, actuator.performed.size) // it tried once
    }

    @Test
    fun settlingProtocol_abortsOnStaleTarget() {
        // Target present at perceive, gone when we re-read just before acting.
        val perceiver = SequencePerceiver(
            listOf(screenWith("Battery"), screenWith("Wi-Fi")),
        )
        val actuator = RecordingActuator(succeed = true)
        val result = loop(
            perceiver,
            org.agentnativeos.core.model.ScriptedModelProvider(listOf(AgentAction.Tap("Battery"))),
            actuator,
        ).run("open battery")

        assertTrue(result is LoopResult.Aborted)
        result as LoopResult.Aborted
        assertTrue(result.reason.contains("stale target"))
        assertTrue("must NOT act on a stale target", actuator.performed.isEmpty())
    }

    @Test
    fun highSideEffect_requiresConfirm_approvedProceeds() {
        val actuator = RecordingActuator(succeed = true)
        val audit = AuditLog()
        val result = loop(
            StaticPerceiver(screenWith("Send payment")),
            org.agentnativeos.core.model.ScriptedModelProvider(
                listOf(AgentAction.Tap("Send payment"), AgentAction.Done("sent")),
            ),
            actuator, approveAll, audit,
        ).run("pay the bill")

        assertTrue(result is LoopResult.Completed)
        assertTrue(audit.entries().any { it is NarrationEvent.Propose && it.highSideEffect })
        assertTrue(audit.entries().any { it is NarrationEvent.Confirm && it.approved })
        assertEquals(1, actuator.performed.size)
    }

    @Test
    fun highSideEffect_deniedAbortsWithoutActing() {
        val actuator = RecordingActuator(succeed = true)
        val result = loop(
            StaticPerceiver(screenWith("Transfer money")),
            org.agentnativeos.core.model.ScriptedModelProvider(listOf(AgentAction.Tap("Transfer money"))),
            actuator, denyAll,
        ).run("move money")

        assertTrue(result is LoopResult.Aborted)
        assertFalse("denied high-side-effect action must not execute", actuator.performed.isNotEmpty())
    }

    @Test
    fun emptyScreenStillTerminatesViaDone() {
        val empty = Observation("com.test", FakeNode(), 0L)
        val result = loop(
            StaticPerceiver(empty),
            org.agentnativeos.core.model.ScriptedModelProvider(listOf(AgentAction.Done("nothing to do"))),
            RecordingActuator(),
        ).run("noop")
        assertTrue(result is LoopResult.Completed)
    }
}

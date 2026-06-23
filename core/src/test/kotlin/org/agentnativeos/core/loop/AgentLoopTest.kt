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
    fun actionFailure_retriesThenAbortsWithHandoff() {
        // Every attempt fails; the loop re-plans a few times, then hands off.
        val actuator = RecordingActuator(succeed = false, detail = "not clickable")
        val result = loop(
            StaticPerceiver(screenWith("Battery")),
            org.agentnativeos.core.model.ModelProvider { AgentAction.Tap("Battery") },
            actuator,
        ).run("open battery")

        assertTrue(result is LoopResult.Aborted)
        result as LoopResult.Aborted
        assertTrue(result.reason.contains("couldn't"))
        assertEquals("nothing yet", result.gotAsFar)
        assertTrue("retries before giving up", actuator.performed.size in 2..4)
    }

    @Test
    fun actionFailure_recoversByTryingADifferentAction() {
        // The timer field can't be typed into; the model then taps a digit key,
        // which succeeds — the run must recover, not abort on the first failure.
        val actuator = object : Actuator {
            val performed = mutableListOf<AgentAction>()
            override fun act(action: AgentAction): ActionOutcome {
                performed.add(action)
                val typedTimer = action is AgentAction.TypeText && action.targetQuery == "00:00.00"
                return ActionOutcome(!typedTimer, if (typedTimer) "setText" else "")
            }
        }
        val result = loop(
            StaticPerceiver(screenWith("00:00.00", "5")),
            org.agentnativeos.core.model.ScriptedModelProvider(
                listOf(AgentAction.TypeText("00:00.00", "5"), AgentAction.Tap("5"), AgentAction.Done("timer set")),
            ),
            actuator,
        ).run("start a 5 minute timer")

        assertTrue(result is LoopResult.Completed)
        assertEquals(
            listOf(AgentAction.TypeText("00:00.00", "5"), AgentAction.Tap("5")),
            actuator.performed,
        )
    }

    @Test
    fun settlingProtocol_replansOnStaleTargetInsteadOfActing() {
        // Battery is present at plan time but gone at the pre-act re-read; the loop
        // must NOT act on it, but instead re-plan against the fresh screen (Wi-Fi).
        val perceiver = SequencePerceiver(
            listOf(screenWith("Battery"), screenWith("Wi-Fi")), // last frame repeats
        )
        val actuator = RecordingActuator(succeed = true)
        val result = loop(
            perceiver,
            org.agentnativeos.core.model.ScriptedModelProvider(
                listOf(AgentAction.Tap("Battery"), AgentAction.Tap("Wi-Fi")),
            ),
            actuator,
        ).run("open settings")

        assertTrue(result is LoopResult.Completed)
        // It recovered: it acted only on the live target, never on the stale one.
        assertEquals(listOf(AgentAction.Tap("Wi-Fi")), actuator.performed)
    }

    @Test
    fun settlingProtocol_abortsAfterRepeatedStaleTargets() {
        // The target is stale on every re-read (plan sees Battery, settle sees Gone),
        // so re-planning can't recover -> abort after the cap, never acting.
        val perceiver = object : Perceiver {
            private var n = 0
            override fun perceive(): Observation =
                if (n++ % 2 == 0) screenWith("Battery") else screenWith("Gone")
        }
        val actuator = RecordingActuator(succeed = true)
        val result = loop(
            perceiver,
            org.agentnativeos.core.model.ModelProvider { AgentAction.Tap("Battery") },
            actuator,
        ).run("open battery")

        assertTrue(result is LoopResult.Aborted)
        assertTrue((result as LoopResult.Aborted).reason.contains("stale target"))
        assertTrue("must NEVER act on a stale target", actuator.performed.isEmpty())
    }

    @Test
    fun stuckDetection_abortsWhenRepeatingTheSameActionWithoutProgress() {
        // The screen never changes and the model keeps proposing the same tap;
        // the loop must give up after a few repeats instead of spinning to budget.
        val actuator = RecordingActuator(succeed = true)
        val result = loop(
            StaticPerceiver(screenWith("Annulla")),
            org.agentnativeos.core.model.ModelProvider { AgentAction.Tap("Annulla") },
            actuator,
        ).run("dismiss it")

        assertTrue(result is LoopResult.Aborted)
        assertTrue((result as LoopResult.Aborted).reason.contains("stuck"))
        // It stopped well short of the 25-step budget.
        assertTrue("should bail after a few repeats", actuator.performed.size <= 4)
    }

    @Test
    fun noProgress_tellsTheModelWhenAnActionLeftTheScreenUnchanged() {
        // The tap "succeeds" but the screen never changes — a dead end, e.g. tapping
        // "Solo una volta" on an app chooser before an app row is selected. The loop
        // must feed that back so the model tries something else BEFORE stuck-abort.
        val seenErrors = mutableListOf<String?>()
        val provider = object : org.agentnativeos.core.model.ModelProvider {
            private var n = 0
            override fun nextAction(context: org.agentnativeos.core.model.PlanningContext): AgentAction {
                seenErrors.add(context.lastError)
                return if (n++ == 0) AgentAction.Tap("Solo una volta") else AgentAction.Done("done")
            }
        }
        val result = loop(
            StaticPerceiver(screenWith("Gmail", "PayPal", "Solo una volta")),
            provider,
            RecordingActuator(succeed = true),
        ).run("send an email")

        assertTrue(result is LoopResult.Completed)
        // First plan saw no error; the plan after the dead tap was told the screen
        // didn't change so it could pick a different element.
        assertEquals(null, seenErrors.first())
        assertTrue(
            "no-progress signal must be fed back to the planner",
            seenErrors.any { it?.contains("did not change the screen") == true },
        )
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

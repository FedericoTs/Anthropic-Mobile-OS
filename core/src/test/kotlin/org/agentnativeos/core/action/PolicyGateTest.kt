package org.agentnativeos.core.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGateTest {

    private val gate = PolicyGate()

    @Test
    fun readOnlyAndReversibleActionsAreAllowed() {
        assertEquals(PolicyDecision.Allow, gate.decide(AgentAction.Home))
        assertEquals(PolicyDecision.Allow, gate.decide(AgentAction.Back))
        assertEquals(PolicyDecision.Allow, gate.decide(AgentAction.LaunchApp("com.x")))
        assertEquals(PolicyDecision.Allow, gate.decide(AgentAction.Tap("Settings")))
        assertEquals(PolicyDecision.Allow, gate.decide(AgentAction.TypeText("Search", "weather")))
    }

    @Test
    fun highSideEffectTapsRequireConfirm() {
        for (q in listOf("Send", "Pay now", "Transfer £200", "Confirm order", "Delete account", "Place order")) {
            val d = gate.decide(AgentAction.Tap(q))
            assertTrue("expected confirm for \"$q\" but got $d", d is PolicyDecision.RequireConfirm)
        }
    }

    @Test
    fun localizedHighSideEffectTapsAlsoRequireConfirm() {
        // The agent acts on localized UIs — the gate must not fail open off English.
        val localized = listOf(
            "Invia", "Paga ora", "Elimina account", "Conferma ordine", // Italian
            "Enviar", "Pagar", "Eliminar",                              // Spanish
            "Envoyer", "Payer", "Supprimer",                            // French
            "Senden", "Bezahlen", "Löschen",                            // German
        )
        for (q in localized) {
            val d = gate.decide(AgentAction.Tap(q))
            assertTrue("expected confirm for \"$q\" but got $d", d is PolicyDecision.RequireConfirm)
        }
    }

    @Test
    fun classificationMatchesDecision() {
        assertEquals(SideEffect.IRREVERSIBLE, gate.classify(AgentAction.Tap("Send money")))
        assertEquals(SideEffect.REVERSIBLE, gate.classify(AgentAction.Tap("Next")))
        assertEquals(SideEffect.READ_ONLY, gate.classify(AgentAction.Home))
    }
}

package org.agentnativeos.core.action

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data class RequireConfirm(val reason: String) : PolicyDecision
    data class Block(val reason: String) : PolicyDecision
}

/**
 * The out-of-model policy gate. It runs AFTER the model proposes an action and
 * BEFORE actuation, and it cannot be overridden by the model or by screen
 * content. High-side-effect (irreversible / money / identity) actions ALWAYS
 * require explicit confirmation regardless of model confidence — the v0 security
 * stance. Because the gate lives outside the model, a prompt injection cannot
 * talk past it.
 */
class PolicyGate(
    private val highRiskWords: List<String> = DEFAULT_HIGH_RISK,
) {
    fun classify(action: AgentAction): SideEffect = when (action) {
        is AgentAction.Tap ->
            if (looksHighRisk(action.targetQuery)) SideEffect.IRREVERSIBLE else SideEffect.REVERSIBLE
        is AgentAction.TypeText -> SideEffect.REVERSIBLE
        is AgentAction.Scroll -> SideEffect.REVERSIBLE
        is AgentAction.Invoke ->
            if (action.capability.lowercase() in HIGH_RISK_CAPABILITIES) SideEffect.IRREVERSIBLE else SideEffect.REVERSIBLE
        is AgentAction.LaunchApp, AgentAction.Back, AgentAction.Home -> SideEffect.READ_ONLY
        is AgentAction.Done, is AgentAction.Abort -> SideEffect.READ_ONLY
    }

    fun decide(action: AgentAction): PolicyDecision = when (classify(action)) {
        SideEffect.READ_ONLY, SideEffect.REVERSIBLE -> PolicyDecision.Allow
        SideEffect.IRREVERSIBLE -> PolicyDecision.RequireConfirm(
            "High side-effect action (${action.label()}) — confirm before commit.",
        )
    }

    private fun looksHighRisk(s: String): Boolean =
        highRiskWords.any { s.contains(it, ignoreCase = true) }

    companion object {
        /** Capabilities that auto-commit (place a call, send now) require confirmation. */
        val HIGH_RISK_CAPABILITIES = setOf("call", "place_call", "send_now", "pay")

        /**
         * High-side-effect words that force a confirm. The agent acts on localized UIs, so an
         * English-only list silently fails open on a non-English phone (a "send"/"pay"/"delete"
         * button labelled "Invia"/"Paga"/"Elimina" would never be gated). We cover the common
         * destructive/financial verbs across the major Latin-script locales. Substring match,
         * lowercase. (A model-based risk classifier is the v1 upgrade — see TODOS.)
         */
        val DEFAULT_HIGH_RISK = listOf(
            // English
            "send", "pay", "transfer", "confirm", "delete", "remove", "buy",
            "purchase", "checkout", "place order", "order now", "submit", "withdraw", "wire",
            // Italian
            "invia", "inviare", "paga", "pagare", "pagamento", "bonifico", "trasferisci",
            "conferma", "confermare", "elimina", "eliminare", "rimuovi", "cancella",
            "acquista", "acquistare", "compra", "ordina", "preleva", "prelievo",
            // Spanish
            "enviar", "envía", "pagar", "pago", "transferir", "transferencia", "confirmar",
            "eliminar", "borrar", "comprar", "pedir", "retirar",
            // French
            "envoyer", "envoie", "payer", "paiement", "transférer", "virement", "confirmer",
            "supprimer", "effacer", "acheter", "commander", "retirer",
            // German
            "senden", "zahlen", "bezahlen", "überweisen", "überweisung", "bestätigen",
            "löschen", "entfernen", "kaufen", "bestellen", "abheben",
            // Portuguese
            "enviar", "envia", "pagar", "pagamento", "transferir", "confirmar",
            "excluir", "apagar", "comprar", "encomendar", "sacar",
        )
    }
}

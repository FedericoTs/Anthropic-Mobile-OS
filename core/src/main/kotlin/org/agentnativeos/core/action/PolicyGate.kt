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
        val DEFAULT_HIGH_RISK = listOf(
            "send", "pay", "transfer", "confirm", "delete", "remove", "buy",
            "purchase", "checkout", "place order", "submit", "withdraw", "wire",
        )
    }
}

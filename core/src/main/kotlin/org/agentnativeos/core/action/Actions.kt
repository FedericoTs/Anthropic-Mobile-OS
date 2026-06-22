package org.agentnativeos.core.action

/**
 * The FIXED, typed tool schema the model is allowed to emit (structural injection
 * defense). The planner can only produce one of these typed actions; it cannot
 * emit free-form instructions, and nothing the screen says can widen this set.
 */
sealed interface AgentAction {
    data class Tap(val targetQuery: String) : AgentAction
    data class TypeText(val targetQuery: String, val value: String) : AgentAction
    data class LaunchApp(val packageName: String) : AgentAction
    data object Back : AgentAction
    data object Home : AgentAction
    data class Done(val summary: String) : AgentAction
    data class Abort(val reason: String) : AgentAction
}

/** Side-effect class. The agency dial may only loosen READ_ONLY and REVERSIBLE. */
enum class SideEffect { READ_ONLY, REVERSIBLE, IRREVERSIBLE }

/** The UI element a targeted action refers to, or null for untargeted actions. */
fun AgentAction.uiTarget(): String? = when (this) {
    is AgentAction.Tap -> targetQuery
    is AgentAction.TypeText -> targetQuery
    else -> null
}

/** Human label for an action (used in narration and confirm prompts). */
fun AgentAction.label(): String = when (this) {
    is AgentAction.Tap -> "tap \"$targetQuery\""
    is AgentAction.TypeText -> "type into \"$targetQuery\""
    is AgentAction.LaunchApp -> "open $packageName"
    AgentAction.Back -> "go back"
    AgentAction.Home -> "go home"
    is AgentAction.Done -> "finish"
    is AgentAction.Abort -> "abort"
}

package org.agentnativeos.core.action

/**
 * The FIXED, typed tool schema the model is allowed to emit (structural injection
 * defense). The planner can only produce one of these typed actions; it cannot
 * emit free-form instructions, and nothing the screen says can widen this set.
 */
sealed interface AgentAction {
    data class Tap(val targetQuery: String) : AgentAction
    data class TypeText(val targetQuery: String, val value: String) : AgentAction
    data class Scroll(val targetQuery: String, val direction: ScrollDirection) : AgentAction
    data class LaunchApp(val packageName: String) : AgentAction

    /**
     * Invoke a named device capability directly (e.g. set_timer, dial, open_url)
     * instead of driving the UI — faster and more reliable. The capability set is a
     * fixed allow-list resolved on-device; the model can only name a capability, not
     * synthesize an arbitrary system intent (the structural injection defense holds).
     */
    data class Invoke(val capability: String, val args: Map<String, String> = emptyMap()) : AgentAction

    data object Back : AgentAction
    data object Home : AgentAction
    data class Done(val summary: String) : AgentAction
    data class Abort(val reason: String) : AgentAction
}

/** Scroll/swipe direction for [AgentAction.Scroll] (wheel pickers and lists). */
enum class ScrollDirection { UP, DOWN }

/** Side-effect class. The agency dial may only loosen READ_ONLY and REVERSIBLE. */
enum class SideEffect { READ_ONLY, REVERSIBLE, IRREVERSIBLE }

/** The UI element a targeted action refers to, or null for untargeted actions. */
fun AgentAction.uiTarget(): String? = when (this) {
    is AgentAction.Tap -> targetQuery
    is AgentAction.TypeText -> targetQuery
    is AgentAction.Scroll -> targetQuery
    else -> null
}

/** Human label for an action (used in narration and confirm prompts). */
fun AgentAction.label(): String = when (this) {
    is AgentAction.Tap -> "tap \"$targetQuery\""
    is AgentAction.TypeText -> "type into \"$targetQuery\""
    is AgentAction.Scroll -> "scroll ${direction.name.lowercase()} on \"$targetQuery\""
    is AgentAction.LaunchApp -> "open $packageName"
    is AgentAction.Invoke -> "use $capability"
    AgentAction.Back -> "go back"
    AgentAction.Home -> "go home"
    is AgentAction.Done -> "finish"
    is AgentAction.Abort -> "abort"
}

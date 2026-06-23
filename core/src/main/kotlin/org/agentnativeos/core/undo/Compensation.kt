package org.agentnativeos.core.undo

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.action.SideEffect
import org.agentnativeos.core.action.label

/**
 * How to undo one executed step. Unified with the high-side-effect gate: anything
 * the [PolicyGate] classes IRREVERSIBLE (money / identity / send / delete) gets an
 * explicit "no undo" — the event stream alone cannot invert a sent message.
 */
sealed interface Compensation {
    /** A concrete inverse action plus a human description. */
    data class Undoable(val inverse: AgentAction, val description: String) : Compensation

    /** Committed and irreversible — there is no inverse. */
    data class Irreversible(val reason: String) : Compensation

    /** Nothing to undo (a terminal step). */
    data object None : Compensation
}

/**
 * Decides the compensation for an action, reusing the policy gate's side-effect
 * classification so "what needs confirmation" and "what can be undone" never drift
 * apart.
 */
class CompensationPlanner(private val gate: PolicyGate = PolicyGate()) {

    fun compensationFor(action: AgentAction, priorText: String? = null): Compensation {
        if (gate.classify(action) == SideEffect.IRREVERSIBLE) {
            return Compensation.Irreversible("${action.label()} can't be undone")
        }
        return when (action) {
            is AgentAction.TypeText -> Compensation.Undoable(
                inverse = AgentAction.TypeText(action.targetQuery, priorText ?: ""),
                description = "restore previous text in \"${action.targetQuery}\"",
            )
            is AgentAction.Scroll -> Compensation.Undoable(
                inverse = AgentAction.Scroll(
                    action.targetQuery,
                    if (action.direction == org.agentnativeos.core.action.ScrollDirection.UP) {
                        org.agentnativeos.core.action.ScrollDirection.DOWN
                    } else {
                        org.agentnativeos.core.action.ScrollDirection.UP
                    },
                ),
                description = "scroll back",
            )

            is AgentAction.Tap,
            AgentAction.Back,
            AgentAction.Home,
            is AgentAction.LaunchApp -> Compensation.Undoable(AgentAction.Back, "go back")

            // A fired capability (e.g. set_timer) has no generic UI inverse.
            is AgentAction.Invoke -> Compensation.None

            is AgentAction.Done, is AgentAction.Abort -> Compensation.None
        }
    }
}

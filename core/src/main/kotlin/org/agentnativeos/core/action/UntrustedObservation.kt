package org.agentnativeos.core.action

/**
 * Wraps perceived screen text as DATA inside an explicit delimited channel. The
 * planner's system prompt declares that everything between the fences is
 * untrusted observation, never instructions.
 *
 * This is the structural half of the prompt-injection defense; it reduces (not
 * eliminates) the chance the model is fooled. The part an injection cannot talk
 * past is the out-of-model [PolicyGate], which runs after the model and forces a
 * confirm on every high-side-effect action regardless of what the screen said.
 */
object UntrustedObservation {
    const val OPEN = "<<<UNTRUSTED_SCREEN>>>"
    const val CLOSE = "<<<END_UNTRUSTED_SCREEN>>>"

    fun wrap(screenText: String): String {
        // Neutralize any attempt by screen content to forge our own fences.
        val safe = screenText.replace(OPEN, "<U>").replace(CLOSE, "</U>")
        return "$OPEN\n$safe\n$CLOSE"
    }
}

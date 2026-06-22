package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.UntrustedObservation
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live round-trip against the real Messages API. SKIPS (via Assume) unless
 * ANTHROPIC_API_KEY is set, so the default `./gradlew test` stays offline and
 * free. When it runs it uses the CHEAPEST model (claude-haiku-4-5, ~cents) and a
 * trivial one-screen task, asserting the round-trip yields a usable typed action
 * rather than the failure Abort. Override the model with ANTHROPIC_TEST_MODEL.
 */
class ClaudeLiveSmokeTest {

    @Test
    fun plansOneActionAgainstTheRealApi() {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
        assumeTrue("set ANTHROPIC_API_KEY to run the live smoke test", apiKey != null)

        val modelName = System.getenv("ANTHROPIC_TEST_MODEL")?.takeIf { it.isNotBlank() }
            ?: ModelId.CHEAPEST.name
        val provider = ClaudeModelProvider(AuthMode.ApiKey(apiKey!!), ModelId(modelName))

        val screen = UntrustedObservation.wrap(
            """
            [button] Battery
            [button] Display
            [button] Sound
            """.trimIndent(),
        )
        val action = provider.nextAction(
            PlanningContext(intent = "open battery settings", untrustedScreen = screen, stepIndex = 0),
        )

        // A working round-trip yields a concrete plan, not the failure Abort.
        assertTrue("live model returned $action", action !is AgentAction.Abort)
    }
}

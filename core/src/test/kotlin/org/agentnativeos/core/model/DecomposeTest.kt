package org.agentnativeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The provider's decompose seam: real split via the API shape, fail-safe fallback. */
class DecomposeTest {

    private fun providerReplying(response: HttpResponse): ClaudeModelProvider =
        ClaudeModelProvider(
            AnthropicClient(AuthMode.ApiKey("k"), transport = { _, _, _ -> response }),
        )

    @Test
    fun splitsACompoundIntentViaTheModel() {
        val reply = HttpResponse(
            200,
            """{"content":[{"type":"text","text":"{\"goals\":[\"find a pizzeria nearby\",\"text the address to Marco\"]}"}]}""",
        )
        assertEquals(
            listOf("find a pizzeria nearby", "text the address to Marco"),
            providerReplying(reply).decompose("find a pizzeria nearby and text the address to Marco"),
        )
    }

    @Test
    fun failsSafeToTheOriginalIntent() {
        // Network/HTTP error -> single-agent fallback.
        assertEquals(
            listOf("do the thing and the other"),
            providerReplying(HttpResponse(500, "boom")).decompose("do the thing and the other"),
        )
        // Prose (unparseable) reply -> single-agent fallback.
        val prose = HttpResponse(200, """{"content":[{"type":"text","text":"I would split this into two"}]}""")
        assertEquals(
            listOf("do the thing and the other"),
            providerReplying(prose).decompose("do the thing and the other"),
        )
    }

    @Test
    fun defaultProviderNeverSplits() {
        val scripted = ScriptedModelProvider(emptyList())
        assertEquals(listOf("a and b"), scripted.decompose("a and b"))
    }
}

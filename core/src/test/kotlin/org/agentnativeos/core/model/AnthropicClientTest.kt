package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicClientTest {

    private class CapturingTransport(private val response: HttpResponse) : HttpTransport {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String = ""
        override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            this.url = url; this.headers = headers; this.body = body
            return response
        }
    }

    private val okReply = HttpResponse(
        200,
        """{"content":[{"type":"text","text":"{\"action\":\"tap\",\"target\":\"Battery\"}"}],"stop_reason":"end_turn"}""",
    )

    @Test
    fun buildsRequestWithModelHeadersAndExtractsAssistantText() {
        val transport = CapturingTransport(okReply)
        val client = AnthropicClient(AuthMode.ApiKey("sk-x"), ModelId("claude-haiku-4-5"), transport = transport)

        val text = client.complete("sys", "usr")

        assertEquals("""{"action":"tap","target":"Battery"}""", text)
        assertEquals(AgentAction.Tap("Battery"), ActionJson.parse(text))
        assertEquals("https://api.anthropic.com/v1/messages", transport.url)
        assertEquals("sk-x", transport.headers["x-api-key"])
        assertTrue(transport.body.contains(""""model":"claude-haiku-4-5""""))
        assertTrue(transport.body.contains(""""system":"sys""""))
    }

    @Test
    fun oauthRequestCarriesBearerAndBetaOptIn() {
        val transport = CapturingTransport(okReply)
        AnthropicClient(AuthMode.SubscriptionOAuth("tok"), transport = transport).complete("s", "u")
        assertEquals("Bearer tok", transport.headers["authorization"])
        assertEquals("oauth-2025-04-20", transport.headers["anthropic-beta"])
    }

    @Test
    fun concatenatesTextBlocksAndIgnoresNonText() {
        val transport = CapturingTransport(
            HttpResponse(200, """{"content":[{"type":"text","text":"a"},{"type":"thinking","text":"z"},{"type":"text","text":"b"}]}"""),
        )
        assertEquals("ab", AnthropicClient(AuthMode.ApiKey("k"), transport = transport).complete("s", "u"))
    }

    @Test
    fun nonSuccessThrowsWithStatusCode() {
        val transport = CapturingTransport(HttpResponse(401, """{"error":"bad key"}"""))
        val client = AnthropicClient(AuthMode.ApiKey("k"), transport = transport)
        val error = runCatching { client.complete("s", "u") }.exceptionOrNull()
        assertTrue(error is RuntimeException)
        assertTrue(error!!.message!!.contains("401"))
    }
}

package org.agentnativeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicHeadersTest {

    @Test
    fun apiKeyGoesOnXApiKeyAndNothingElse() {
        val h = AnthropicHeaders.forAuth(AuthMode.ApiKey("sk-abc"))
        assertEquals("sk-abc", h["x-api-key"])
        // Never both credentials: an x-api-key + bearer pair makes the API 401.
        assertNull(h["authorization"])
        assertNull(h["anthropic-beta"])
    }

    @Test
    fun oauthTokenGoesOnBearerWithTheBetaOptIn() {
        val h = AnthropicHeaders.forAuth(AuthMode.SubscriptionOAuth("tok-123"))
        assertEquals("Bearer tok-123", h["authorization"])
        // The /v1/messages endpoint rejects an OAuth bearer without this opt-in.
        assertEquals("oauth-2025-04-20", h["anthropic-beta"])
        assertNull(h["x-api-key"])
    }

    @Test
    fun bothModesCarryVersionAndContentType() {
        for (auth in listOf(AuthMode.ApiKey("k"), AuthMode.SubscriptionOAuth("t"))) {
            val h = AnthropicHeaders.forAuth(auth)
            assertEquals("application/json", h["content-type"])
            assertEquals("2023-06-01", h["anthropic-version"])
        }
    }

    @Test
    fun exactlyOneCredentialHeaderPerMode() {
        val apiKey = AnthropicHeaders.forAuth(AuthMode.ApiKey("k"))
        val oauth = AnthropicHeaders.forAuth(AuthMode.SubscriptionOAuth("t"))
        assertTrue("x-api-key" in apiKey.keys)
        assertFalse("authorization" in apiKey.keys)
        assertTrue("authorization" in oauth.keys)
        assertFalse("x-api-key" in oauth.keys)
    }
}

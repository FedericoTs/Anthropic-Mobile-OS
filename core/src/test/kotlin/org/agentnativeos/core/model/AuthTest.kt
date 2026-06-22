package org.agentnativeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {

    @Test
    fun prefersApiKeyWhenPresent() {
        assertEquals(AuthMode.ApiKey("sk-abc"), Auth.choose("sk-abc", "tok"))
    }

    @Test
    fun fallsBackToOAuthWhenNoApiKey() {
        assertEquals(AuthMode.SubscriptionOAuth("tok"), Auth.choose(null, "tok"))
        assertEquals(AuthMode.SubscriptionOAuth("tok"), Auth.choose("   ", "tok"))
    }

    @Test
    fun trimsCredentials() {
        assertEquals(AuthMode.ApiKey("sk-abc"), Auth.choose("  sk-abc  ", null))
    }

    @Test
    fun nullWhenNothingConfigured() {
        assertNull(Auth.choose(null, null))
        assertNull(Auth.choose("", "   "))
        assertFalse(Auth.isConfigured("", null))
        assertTrue(Auth.isConfigured("sk-abc", null))
    }
}

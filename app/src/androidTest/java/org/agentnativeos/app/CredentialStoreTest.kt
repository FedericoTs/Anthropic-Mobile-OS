package org.agentnativeos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the encrypted credential store round-trips and clears on a real device. */
@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {

    @Test
    fun storesPersistsAndClearsApiKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = CredentialStore(context)
        store.clear()
        assertFalse(store.isConfigured)

        store.apiKey = "sk-test-12345"
        assertTrue(store.isConfigured)

        // A fresh instance reads the persisted, decrypted value.
        assertEquals("sk-test-12345", CredentialStore(context).apiKey)

        store.clear()
        assertFalse(store.isConfigured)
        assertEquals(null, CredentialStore(context).apiKey)
    }

    @Test
    fun storesPersistsAndClearsSubscriptionToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = CredentialStore(context)
        store.clear()
        assertFalse(store.isConfigured)

        // A subscription token alone is enough to be "configured" (no API key).
        store.oauthToken = "tok-test-67890"
        assertTrue(store.isConfigured)
        assertEquals("tok-test-67890", CredentialStore(context).oauthToken)

        store.clear()
        assertFalse(store.isConfigured)
        assertEquals(null, CredentialStore(context).oauthToken)
    }
}

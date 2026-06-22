package org.agentnativeos.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device, encrypted storage for the model credential — either an Anthropic
 * API key or a Claude subscription (OAuth bearer) token. Both are stored in
 * EncryptedSharedPreferences (AES-256, Android keystore-backed). Neither ever
 * leaves the device except inside the TLS request to Anthropic, and neither is
 * ever logged or placed in the narration stream (the dumper already redacts
 * password fields).
 *
 * The API key is the default, supported path. The subscription token is a
 * bring-your-own credential (e.g. obtained via `ant auth print-credentials
 * --access-token`); these tokens are short-lived, so a saved one can expire and
 * need re-pasting. We deliberately do NOT run a first-party "Sign in with Claude"
 * browser flow here — there is no public OAuth client program for that.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value.trim())
            }.apply()
        }

    /** A Claude subscription (OAuth bearer) token, used when no API key is set. */
    var oauthToken: String?
        get() = prefs.getString(KEY_OAUTH, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_OAUTH) else putString(KEY_OAUTH, value.trim())
            }.apply()
        }

    /** Configured if either credential is present (Auth.choose prefers the API key). */
    val isConfigured: Boolean get() = !apiKey.isNullOrBlank() || !oauthToken.isNullOrBlank()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "agent_credentials"
        const val KEY_API = "anthropic_api_key"
        const val KEY_OAUTH = "claude_oauth_token"
    }
}

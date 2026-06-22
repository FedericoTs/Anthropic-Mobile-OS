package org.agentnativeos.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device, encrypted storage for the model credential. The key is stored in
 * EncryptedSharedPreferences (AES-256, Android keystore-backed). It never leaves
 * the device except inside the TLS request to Anthropic, and it is never logged
 * or placed in the narration stream (the dumper already redacts password fields).
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

    val isConfigured: Boolean get() = !apiKey.isNullOrBlank()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE = "agent_credentials"
        const val KEY_API = "anthropic_api_key"
    }
}

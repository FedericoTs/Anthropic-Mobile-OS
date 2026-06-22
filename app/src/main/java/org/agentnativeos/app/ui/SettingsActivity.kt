package org.agentnativeos.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.R

/**
 * Configure the model credential: an Anthropic API key (the default path) and/or
 * a Claude subscription login token. Both are stored encrypted on-device via
 * CredentialStore; Auth.choose prefers the API key when both are present.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = CredentialStore(this)

        val keyInput = findViewById<EditText>(R.id.input_key)
        val tokenInput = findViewById<EditText>(R.id.input_token)

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            store.apiKey = keyInput.text.toString()
            keyInput.text.clear()
            updateStatus()
            toast(getString(R.string.settings_saved))
        }
        findViewById<Button>(R.id.btn_save_login).setOnClickListener {
            store.oauthToken = tokenInput.text.toString()
            tokenInput.text.clear()
            updateStatus()
            toast(getString(R.string.settings_saved))
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            store.clear()
            updateStatus()
            toast(getString(R.string.settings_cleared))
        }
        updateStatus()
    }

    private fun updateStatus() {
        val hasKey = !store.apiKey.isNullOrBlank()
        val hasToken = !store.oauthToken.isNullOrBlank()
        findViewById<TextView>(R.id.txt_status).text = getString(
            when {
                hasKey && hasToken -> R.string.settings_status_both
                hasKey -> R.string.settings_status_apikey
                hasToken -> R.string.settings_status_token
                else -> R.string.settings_not_configured
            },
        )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

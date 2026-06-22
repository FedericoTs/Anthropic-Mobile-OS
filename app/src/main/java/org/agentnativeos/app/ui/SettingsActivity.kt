package org.agentnativeos.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.R

/** Enter / clear the Anthropic API key. Stored encrypted on-device via CredentialStore. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = CredentialStore(this)

        val input = findViewById<EditText>(R.id.input_key)
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            store.apiKey = input.text.toString()
            input.text.clear()
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
        findViewById<TextView>(R.id.txt_status).text = getString(
            if (store.isConfigured) R.string.settings_configured else R.string.settings_not_configured,
        )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

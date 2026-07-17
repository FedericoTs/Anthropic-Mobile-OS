package org.agentnativeos.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import org.agentnativeos.app.AutonomyPreferences
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.R
import org.agentnativeos.core.model.ModelCatalog
import org.agentnativeos.core.model.ModelOption

/**
 * Configure the planner: which model runs intents (the picker; cheapest is the
 * default), plus the credential — an Anthropic API key and/or a Claude
 * subscription login token. Stored on-device; Auth.choose prefers the API key.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var store: CredentialStore
    private lateinit var models: ModelPreferences
    private lateinit var autonomy: AutonomyPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = CredentialStore(this)
        models = ModelPreferences(this)
        autonomy = AutonomyPreferences(this)

        renderModelPicker()

        findViewById<SwitchCompat>(R.id.switch_autonomy).apply {
            isChecked = autonomy.autonomous
            setOnCheckedChangeListener { _, checked ->
                autonomy.autonomous = checked
                toast(getString(if (checked) R.string.settings_autonomy_on else R.string.settings_autonomy_off))
            }
        }

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

    /** Build one selectable row per catalog model; the chosen one is coral-checked. */
    private fun renderModelPicker() {
        val list = findViewById<LinearLayout>(R.id.model_list)
        list.removeAllViews()
        val selected = models.selected
        for (option in ModelCatalog.OPTIONS) {
            list.addView(modelRow(option, isSelected = option.id == selected))
        }
    }

    // The row copy is model-catalog data (model names + guidance), not localizable
    // UI chrome; the ✓ is a status glyph mirroring the narration "done" check.
    @SuppressLint("SetTextI18n")
    private fun modelRow(option: ModelOption, isSelected: Boolean): View {
        val pad = dp(14)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_input)
            setPadding(pad, pad, pad, pad)
            minimumHeight = dp(56)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
            contentDescription = "${option.label}. ${option.tagline}. ${option.bestFor}." +
                if (isSelected) " Selected." else ""
            setOnClickListener {
                models.selected = option.id
                renderModelPicker()
                toast(getString(R.string.settings_model_selected, option.label))
            }
        }

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(
            TextView(this).apply {
                text = option.label
                textSize = 16f
                setTextColor(color(R.color.text))
                setTypeface(typeface, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            },
        )
        texts.addView(
            TextView(this).apply {
                text = "${option.tagline} · ${option.bestFor}"
                textSize = 13f
                setTextColor(color(R.color.muted))
                setPadding(0, dp(2), 0, 0)
            },
        )
        row.addView(texts)

        row.addView(
            TextView(this).apply {
                text = if (isSelected) "✓" else ""
                textSize = 18f
                setTextColor(color(R.color.accent))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        return row
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

package org.agentnativeos.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.HomeAction
import org.agentnativeos.app.HomeRouter
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.PersistentTaskMemory
import org.agentnativeos.app.PredictionStore
import org.agentnativeos.app.R
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.memory.TaskStatus
import org.agentnativeos.core.model.ModelCatalog
import org.agentnativeos.core.predict.Suggestion

/**
 * T5: the static ambient home — the calm resting face. The intent input is the
 * hero; the bottom bar carries the swap-model pill and the app-grid escape hatch.
 * "open X" launches an app directly (the always-available escape hatch); other
 * intents go to the agent once the service + a model are configured.
 */
class HomeActivity : AppCompatActivity() {

    private val predictions by lazy { PredictionStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val input = findViewById<EditText>(R.id.input_intent)
        findViewById<Button>(R.id.btn_go).setOnClickListener { submit(input.text.toString()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit(input.text.toString()); true
            } else {
                false
            }
        }
        findViewById<Button>(R.id.btn_apps).setOnClickListener {
            startActivity(Intent(this, AppGridActivity::class.java))
        }
        findViewById<Button>(R.id.btn_swap_model).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        // Reflect the chosen model on the pill (DESIGN: dot + model name + swap).
        val model = ModelCatalog.byId(ModelPreferences(this).selected)
        findViewById<Button>(R.id.btn_swap_model).text =
            getString(R.string.home_model_pill, model.shortLabel)

        // Recent activity (agent memory) — the home's quiet record of what it did.
        val recent = PersistentTaskMemory(this).recent(3)
        findViewById<TextView>(R.id.txt_recent).text =
            if (recent.isEmpty()) {
                getString(R.string.home_recent_none)
            } else {
                recent.joinToString("\n") {
                    val mark = if (it.status == TaskStatus.COMPLETED) "✓" else "⚠"
                    "$mark ${it.intent}"
                }
            }

        renderSuggestions()
    }

    /** The predictive "Right now" section: learned suggestions for this moment (hidden if none). */
    private fun renderSuggestions() {
        val section = findViewById<LinearLayout>(R.id.suggestions_section)
        val list = findViewById<LinearLayout>(R.id.suggestions_list)
        list.removeAllViews()
        val suggestions = predictions.suggestionsNow(PersistentTaskMemory(this))
        if (suggestions.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        suggestions.forEach { list.addView(suggestionRow(it)) }
        section.visibility = View.VISIBLE
        predictions.recordShown(suggestions.map { it.intent })
    }

    private fun suggestionRow(suggestion: Suggestion): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@HomeActivity, R.drawable.bg_input)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            minimumHeight = dp(56)
            isClickable = true
            contentDescription = suggestion.intent
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { runSuggestion(suggestion.intent) }
            setOnLongClickListener { dismissSuggestion(suggestion.intent); true }
        }
        row.addView(
            TextView(this).apply {
                text = suggestion.intent
                textSize = 16f
                setTextColor(color(R.color.text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            Button(this).apply {
                text = getString(R.string.home_suggestion_start)
                setTextColor(color(R.color.on_accent))
                backgroundTintList = ColorStateList.valueOf(color(R.color.accent))
                isAllCaps = false
                minWidth = dp(72)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44),
                ).apply { marginStart = dp(12) }
                setOnClickListener { runSuggestion(suggestion.intent) }
            },
        )
        return row
    }

    private fun runSuggestion(intent: String) {
        predictions.recordTapped(intent)
        submit(intent) // runs through the NORMAL loop (gate and all) — a tap, never auto-run
    }

    private fun dismissSuggestion(intent: String) {
        predictions.recordDismissed(intent)
        toast(getString(R.string.home_suggestion_dismissed))
        renderSuggestions()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun submit(raw: String) {
        val hasModel = CredentialStore(this).isConfigured
        val serviceEnabled = AgentAccessibilityService.instance != null
        when (val action = HomeRouter.route(raw, hasModel, serviceEnabled)) {
            HomeAction.Ignore -> Unit
            HomeAction.ShowDemo -> startActivity(
                Intent(this, NarrationActivity::class.java).putExtra(NarrationActivity.EXTRA_DEMO, true),
            )
            is HomeAction.OpenApp ->
                if (!launchByLabel(action.query)) {
                    // The fast launcher missed (e.g. a localized label). If the agent
                    // is ready, let it try the full intent instead of dead-ending.
                    val ready = AgentAccessibilityService.instance != null && CredentialStore(this).isConfigured
                    if (ready) launchAgent(raw) else toast(getString(R.string.app_not_found, action.query))
                }
            HomeAction.NeedService -> {
                toast(getString(R.string.needs_service))
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            HomeAction.NeedModel -> {
                toast(getString(R.string.needs_model))
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            is HomeAction.RunAgent -> launchAgent(action.intent)
        }
    }

    private fun launchAgent(intent: String) {
        if (!ensureOverlayPermission()) return // sent the user to grant it; they'll tap Go again
        startActivity(
            Intent(this, NarrationActivity::class.java)
                .putExtra(NarrationActivity.EXTRA_RUN, true)
                .putExtra(NarrationActivity.EXTRA_INTENT, intent),
        )
    }

    /**
     * Ask once for "Display over other apps" so the agent can float its confirm gate
     * over whatever app it drives. Returns true to proceed with the run (granted, or
     * already asked — the in-app card is the fallback), false if we just opened the
     * settings screen for the user to grant it.
     */
    private fun ensureOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        val prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OVERLAY_ASKED, false)) return true // don't nag; fall back to in-app card
        prefs.edit().putBoolean(KEY_OVERLAY_ASKED, true).apply()
        toast(getString(R.string.overlay_rationale))
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
        return false
    }

    private fun launchByLabel(query: String): Boolean {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(main, 0)
            .firstOrNull { it.loadLabel(pm).toString().contains(query, ignoreCase = true) }
            ?: return false
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        toast(getString(R.string.opening, match.loadLabel(pm).toString()))
        return true
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val UI_PREFS = "agent_ui"
        const val KEY_OVERLAY_ASKED = "overlay_asked"
    }
}

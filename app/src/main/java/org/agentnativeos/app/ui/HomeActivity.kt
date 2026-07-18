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
import org.agentnativeos.app.AppLaunchLog
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.HomeAction
import org.agentnativeos.app.HomeRouter
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.PersistentTaskMemory
import org.agentnativeos.app.PredictionStore
import org.agentnativeos.app.R
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.model.ModelCatalog
import org.agentnativeos.core.predict.AppUsageRanker
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
        findViewById<TextView>(R.id.txt_greeting).text = greeting()
        findViewById<TextView>(R.id.txt_clock).text = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))

        // Reflect the chosen model on the pill (DESIGN: dot + model name + swap).
        val model = ModelCatalog.byId(ModelPreferences(this).selected)
        findViewById<Button>(R.id.btn_swap_model).text =
            getString(R.string.home_model_pill, model.shortLabel)

        renderSuggestions()
        renderAppsNow()
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
            setPadding(dp(18), dp(14), dp(14), dp(14))
            minimumHeight = dp(64)
            isClickable = true
            contentDescription = suggestion.intent
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
            setOnClickListener { runSuggestion(suggestion.intent) }
            setOnLongClickListener { dismissSuggestion(suggestion.intent); true }
        }
        row.addView(
            TextView(this).apply {
                text = suggestion.intent
                textSize = 16f
                setTextColor(color(R.color.text))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(dp(2).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            Button(this).apply {
                text = getString(R.string.home_suggestion_start)
                setTextColor(color(R.color.on_accent))
                backgroundTintList = ColorStateList.valueOf(color(R.color.accent))
                isAllCaps = false
                minWidth = dp(76)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44),
                ).apply { marginStart = dp(14) }
                setOnClickListener { runSuggestion(suggestion.intent) }
            },
        )
        return row
    }

    /** The predictive app shelf: apps you tend to open around now, most-recent as fallback. */
    private fun renderAppsNow() {
        val section = findViewById<LinearLayout>(R.id.apps_now_section)
        val row = findViewById<LinearLayout>(R.id.apps_now_row)
        row.removeAllViews()

        val zone = java.time.ZoneId.systemDefault()
        val ranked = AppUsageRanker().rank(
            AppLaunchLog(this).events(zone),
            org.agentnativeos.core.predict.NowContext.at(System.currentTimeMillis(), zone),
            limit = SHELF_SIZE,
        ).toMutableList()

        // Fill remaining slots from the launcher list so the shelf is whole from day one.
        if (ranked.size < SHELF_SIZE) {
            val pm = packageManager
            val launchers = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launchers, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != packageName && it !in ranked }
                .distinct()
                .take(SHELF_SIZE - ranked.size)
                .forEach { ranked.add(it) }
        }

        val tiles = ranked.mapNotNull { appTile(it) }
        if (tiles.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        tiles.forEach { row.addView(it) }
        section.visibility = View.VISIBLE
    }

    /** One shelf tile: rounded surface square with the app icon, label beneath. */
    private fun appTile(pkg: String): View? {
        val pm = packageManager
        val (icon, label) = try {
            pm.getApplicationIcon(pkg) to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            return null // uninstalled since it was logged
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            contentDescription = label
            setOnClickListener {
                pm.getLaunchIntentForPackage(pkg)?.let { launch ->
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    AppLaunchLog(this@HomeActivity).record(pkg)
                }
            }
        }
        column.addView(
            android.widget.FrameLayout(this).apply {
                background = ContextCompat.getDrawable(this@HomeActivity, R.drawable.bg_input)
                layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
                addView(
                    android.widget.ImageView(this@HomeActivity).apply {
                        setImageDrawable(icon)
                        layoutParams = android.widget.FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
                    },
                )
            },
        )
        column.addView(
            TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(color(R.color.muted))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(6), dp(2), 0)
            },
        )
        return column
    }

    /** A warm, localized greeting for the top of the home ("Good morning, Saturday"). */
    private fun greeting(): String {
        val now = java.time.LocalDateTime.now()
        val partRes = when (now.hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
        val weekday = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale.getDefault(),
        )
        return getString(R.string.home_greeting, getString(partRes), weekday)
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
        val pkg = match.activityInfo.packageName
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        AppLaunchLog(this).record(pkg) // feeds the predictive "Apps, right now" row
        toast(getString(R.string.opening, match.loadLabel(pm).toString()))
        return true
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val UI_PREFS = "agent_ui"
        const val KEY_OVERLAY_ASKED = "overlay_asked"
        const val SHELF_SIZE = 5
    }
}

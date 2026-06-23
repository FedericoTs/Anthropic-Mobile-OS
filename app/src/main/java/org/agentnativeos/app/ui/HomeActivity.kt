package org.agentnativeos.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.HomeAction
import org.agentnativeos.app.HomeRouter
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.R
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.model.ModelCatalog

/**
 * T5: the static ambient home — the calm resting face. The intent input is the
 * hero; the bottom bar carries the swap-model pill and the app-grid escape hatch.
 * "open X" launches an app directly (the always-available escape hatch); other
 * intents go to the agent once the service + a model are configured.
 */
class HomeActivity : AppCompatActivity() {

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

    override fun onResume() {
        super.onResume()
        // Reflect the chosen model on the pill (DESIGN: dot + model name + swap).
        val model = ModelCatalog.byId(ModelPreferences(this).selected)
        findViewById<Button>(R.id.btn_swap_model).text =
            getString(R.string.home_model_pill, model.shortLabel)
    }

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

    private fun launchAgent(intent: String) = startActivity(
        Intent(this, NarrationActivity::class.java)
            .putExtra(NarrationActivity.EXTRA_RUN, true)
            .putExtra(NarrationActivity.EXTRA_INTENT, intent),
    )

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
}

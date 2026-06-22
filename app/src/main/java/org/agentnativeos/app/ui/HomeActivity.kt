package org.agentnativeos.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.agentnativeos.app.R
import org.agentnativeos.app.device.AgentAccessibilityService

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
            // M0: model configuration is a follow-up; surface the state for now.
            toast(getString(R.string.needs_model))
        }
    }

    private fun submit(raw: String) {
        val intent = raw.trim()
        if (intent.isEmpty()) return

        // Showcase: "demo" opens the narration feed with a canned run so you can
        // see the agent think + the confirm gate without a model configured.
        if (intent.equals("demo", ignoreCase = true)) {
            startActivity(
                Intent(this, NarrationActivity::class.java)
                    .putExtra(NarrationActivity.EXTRA_DEMO, true),
            )
            return
        }

        // Escape hatch: "open X" launches an app directly, never trapping the user.
        if (intent.startsWith("open ", ignoreCase = true)) {
            val query = intent.substring(5).trim()
            if (!launchByLabel(query)) toast(getString(R.string.app_not_found, query))
            return
        }

        // Agent run requires the service enabled and a model configured (M0 guidance).
        if (AgentAccessibilityService.instance == null) {
            toast(getString(R.string.needs_service))
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        toast(getString(R.string.needs_model))
        // When a model is configured:
        //   AgentController.run(intent, ClaudeModelProvider(auth), confirmHandler)
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
}

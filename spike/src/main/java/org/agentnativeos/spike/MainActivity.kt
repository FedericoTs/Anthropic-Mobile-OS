package org.agentnativeos.spike

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The harness's only screen. It exists so the app is launchable and the
 * AccessibilityService can be enabled; all real driving happens via adb
 * broadcasts to [CommandReceiver].
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_open_a11y_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.txt_status).text =
            getString(if (isServiceEnabled()) R.string.status_enabled else R.string.status_disabled)
    }

    private fun isServiceEnabled(): Boolean {
        val expected = "$packageName/$packageName.SpikeAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

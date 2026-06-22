package org.agentnativeos.spike

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Lets the spike be driven from the host while a *different* app is in the
 * foreground (you can't tap the harness's own UI when you're looking at Settings).
 *
 * Example:
 *   adb shell am broadcast -n org.agentnativeos.spike/.CommandReceiver \
 *     -a org.agentnativeos.spike.TAP --es text "Network & internet"
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = SpikeAccessibilityService.instance
        if (service == null) {
            Log.w(
                TAG,
                "command ${intent.action} ignored: service not connected. " +
                    "Enable \"Spike A11y Harness\" in Settings > Accessibility.",
            )
            return
        }
        when (intent.action) {
            ACTION_DUMP -> {
                val path = service.dumpActiveWindow()
                Log.i(TAG, "DUMP -> $path")
            }

            ACTION_TAP -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                val x = intent.getFloatExtra(EXTRA_X, Float.NaN)
                val y = intent.getFloatExtra(EXTRA_Y, Float.NaN)
                val ok = when {
                    text != null -> service.tapByText(text)
                    !x.isNaN() && !y.isNaN() -> service.tapAt(x, y)
                    else -> {
                        Log.w(TAG, "TAP needs --es text OR --ef x and --ef y")
                        false
                    }
                }
                Log.i(TAG, "TAP(text=$text, x=$x, y=$y) -> $ok")
            }

            ACTION_TYPE -> {
                val value = intent.getStringExtra(EXTRA_TEXT)
                if (value == null) {
                    Log.w(TAG, "TYPE needs --es text")
                    return
                }
                val target = intent.getStringExtra(EXTRA_TARGET)
                val ok = service.typeText(target, value)
                Log.i(TAG, "TYPE(target=$target) -> $ok")
            }

            ACTION_HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ACTION_BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            else -> Log.w(TAG, "unknown action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "SPIKE_CMD"

        const val ACTION_DUMP = "org.agentnativeos.spike.DUMP"
        const val ACTION_TAP = "org.agentnativeos.spike.TAP"
        const val ACTION_TYPE = "org.agentnativeos.spike.TYPE"
        const val ACTION_HOME = "org.agentnativeos.spike.HOME"
        const val ACTION_BACK = "org.agentnativeos.spike.BACK"

        const val EXTRA_TEXT = "text"
        const val EXTRA_TARGET = "target"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
    }
}

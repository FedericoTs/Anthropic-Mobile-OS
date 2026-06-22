package org.agentnativeos.spike

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The T0 spike's perception + actuation engine.
 *
 * Once enabled in Settings -> Accessibility, this service can read the active
 * window's accessibility node tree ([rootInActiveWindow]) and dispatch gestures
 * and text entry across *other* apps. The whole point of T0 is to find out, with
 * evidence, on which classes of app that actually works.
 *
 * It is driven on demand by [CommandReceiver] (adb broadcasts), not by reacting
 * to accessibility events, so the logs stay readable.
 */
class SpikeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "connected — drive with: adb shell am broadcast -n $PACKAGE/.CommandReceiver -a $PACKAGE.DUMP")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally a no-op. The spike is driven via CommandReceiver so that
        // perception happens only when we ask for it.
    }

    override fun onInterrupt() {
        // No-op: nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        Log.i(TAG, "unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Dump the active window's a11y tree to logcat + a file. Returns the file path. */
    fun dumpActiveWindow(): String {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "dumpActiveWindow: rootInActiveWindow == null (nothing perceivable — possibly blocked)")
            return A11yTreeDumper.persist(this, "rootInActiveWindow == null\n", rootPackage = "null")
        }
        val report = A11yTreeDumper.dump(root)
        return A11yTreeDumper.persist(this, report, rootPackage = root.packageName?.toString() ?: "unknown")
    }

    fun tapByText(query: String): Boolean = Actuator.tapByText(this, query)

    fun tapAt(x: Float, y: Float): Boolean = Actuator.tapAt(this, x, y)

    fun typeText(targetQuery: String?, value: String): Boolean =
        Actuator.typeText(this, targetQuery, value)

    companion object {
        const val TAG = "SPIKE"
        const val PACKAGE = "org.agentnativeos.spike"

        /** Set while the service is connected so [CommandReceiver] can reach it. */
        @Volatile
        var instance: SpikeAccessibilityService? = null
    }
}

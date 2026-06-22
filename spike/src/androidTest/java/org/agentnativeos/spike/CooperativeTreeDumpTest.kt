package org.agentnativeos.spike

import android.content.Intent
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC#2 automatable guard, perceive half (see issue #1, "Testing Plan").
 *
 * This does NOT enable [SpikeAccessibilityService] from inside the test —
 * enabling an AccessibilityService programmatically is awkward and usually needs
 * `adb shell settings put secure ...`. Instead it proves the *platform*
 * accessibility perception path works: UiAutomator reads the on-screen node tree
 * through the same accessibility framework the service uses. If a known on-screen
 * string is perceivable here, `rootInActiveWindow` in the service will see it too.
 *
 * The full perceive + act matrix (Tiers 1-3) is run manually with the /scripts
 * driver and recorded in docs/spikes/t0-hostile-app.md.
 */
@RunWith(AndroidJUnit4::class)
class CooperativeTreeDumpTest {

    @Test
    fun cooperativeApp_exposesNonEmptyTreeWithKnownText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.context

        device.pressHome()

        // Tier-1 cooperative target: the AOSP Settings app, present on stock images.
        val launch = context.packageManager
            .getLaunchIntentForPackage(SETTINGS_PKG)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue("$SETTINGS_PKG not installed on this image", launch != null)
        context.startActivity(launch)

        val appeared = device.wait(Until.hasObject(By.pkg(SETTINGS_PKG).depth(0)), TIMEOUT_MS)
        assertTrue("Settings window never appeared", appeared)

        val textNodes = device.findObjects(By.clazz(TextView::class.java)).size
        assertTrue("Expected a non-empty accessibility tree, got $textNodes text nodes", textNodes > 0)

        // Stable labels across AOSP versions; require at least one to be perceivable.
        val knownVisible = device.wait(Until.hasObject(By.textContains("Settings")), SHORT_MS) ||
            device.hasObject(By.textContains("Network")) ||
            device.hasObject(By.textContains("Battery")) ||
            device.hasObject(By.textContains("System"))
        assertTrue("No known Settings label was perceivable in the a11y tree", knownVisible)
    }

    private companion object {
        const val SETTINGS_PKG = "com.android.settings"
        const val TIMEOUT_MS = 10_000L
        const val SHORT_MS = 5_000L
    }
}

package org.agentnativeos.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.agentnativeos.app.ui.NarrationActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device test for the trust surface. Launches the narration feed in demo mode
 * and asserts the live timeline renders, the high-side-effect confirm gate
 * appears (the agent does NOT auto-send), approving it advances the run, and the
 * run reaches its done summary.
 */
@RunWith(AndroidJUnit4::class)
class NarrationFeedTest {

    @Test
    fun demo_rendersTimeline_gatesHighSideEffect_thenCompletes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext

        context.startActivity(
            Intent(context, NarrationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(NarrationActivity.EXTRA_DEMO, true),
        )

        // The live feed shows early steps.
        assertTrue(
            "an early step should appear in the timeline",
            device.wait(Until.hasObject(By.textContains("Messages")), TIMEOUT),
        )

        // The high-side-effect send raises the confirm gate (and does NOT auto-run).
        assertTrue(
            "the confirm card should appear for the send step",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT),
        )

        // Approve it.
        val approve = device.findObject(By.text("Approve"))
        assertNotNull("Approve button present", approve)
        approve.click()

        // The run completes.
        assertTrue(
            "the run should reach its done summary",
            device.wait(Until.hasObject(By.textContains("texted the team")), TIMEOUT),
        )
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}

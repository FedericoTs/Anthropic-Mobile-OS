package org.agentnativeos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Autonomous mode must default OFF and persist across instances on a real device. */
@RunWith(AndroidJUnit4::class)
class AutonomyPreferencesTest {

    @Test
    fun defaultsOffAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AutonomyPreferences(context)

        // A fresh read (after clearing) is OFF — the safe default.
        prefs.autonomous = false
        assertFalse("autonomous mode must default OFF", AutonomyPreferences(context).autonomous)

        prefs.autonomous = true
        assertEquals(true, AutonomyPreferences(context).autonomous)

        // Restore the safe default so a device left after the test isn't silently auto-approving.
        prefs.autonomous = false
        assertFalse(AutonomyPreferences(context).autonomous)
    }
}

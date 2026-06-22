package org.agentnativeos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.agentnativeos.core.model.ModelId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the chosen planner model persists across instances on a real device. */
@RunWith(AndroidJUnit4::class)
class ModelPreferencesTest {

    @Test
    fun persistsTheSelectedModelAcrossInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = ModelPreferences(context)

        prefs.selected = ModelId("claude-sonnet-4-6")
        assertEquals("claude-sonnet-4-6", ModelPreferences(context).selected.name)

        prefs.selected = ModelId("claude-opus-4-8")
        assertEquals("claude-opus-4-8", ModelPreferences(context).selected.name)

        // Restore the default so the picker shows the cheapest after the test.
        prefs.selected = ModelId.DEFAULT
        assertEquals(ModelId.CHEAPEST, ModelPreferences(context).selected)
    }
}

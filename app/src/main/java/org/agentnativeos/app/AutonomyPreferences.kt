package org.agentnativeos.app

import android.content.Context

/**
 * Whether the agent runs fully autonomously — auto-approving EVERY high-side-effect
 * action (send / pay / delete) with no confirm prompt. This deliberately overrides the
 * confirm gate, the app's safety centerpiece, so it is opt-in and defaults OFF; the user
 * owns the risk. Not a secret (a single boolean), so plain SharedPreferences.
 *
 * The gate still CLASSIFIES actions (the narration feed still flags a step as high-side-
 * effect) — autonomous mode only removes the human tap, it does not hide what happened.
 */
class AutonomyPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var autonomous: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) { prefs.edit().putBoolean(KEY, value).apply() }

    private companion object {
        const val FILE = "agent_prefs"
        const val KEY = "autonomous_mode"
    }
}

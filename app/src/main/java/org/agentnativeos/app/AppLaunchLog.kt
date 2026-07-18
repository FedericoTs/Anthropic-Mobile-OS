package org.agentnativeos.app

import android.content.Context
import org.agentnativeos.core.model.Json
import org.agentnativeos.core.predict.UsageEvent
import java.time.ZoneId

/**
 * On-device log of app launches made through OUR surfaces (the home's "open X" fast
 * path, the app grid, the agent's launch action) — the signal behind the predictive
 * "Apps, right now" row. Just package names + timestamps in plain prefs, capped so it
 * can't grow without bound. No system-wide usage access, no permission: we only see
 * what the user (or their agent) did through this OS.
 */
class AppLaunchLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(packageName: String, atMs: Long = System.currentTimeMillis()) {
        val entries = (listOf(listOf(packageName, atMs)) + load()).take(MAX)
        prefs.edit().putString(KEY, Json.encode(entries)).apply()
    }

    /** Launches as time-tagged usage events (package name rides in [UsageEvent.intent]). */
    fun events(zone: ZoneId = ZoneId.systemDefault()): List<UsageEvent> =
        load().mapNotNull { entry ->
            val pkg = entry.getOrNull(0) as? String ?: return@mapNotNull null
            val at = (entry.getOrNull(1) as? Double)?.toLong() ?: return@mapNotNull null
            UsageEvent.at(pkg, at, zone)
        }

    private fun load(): List<List<Any?>> =
        (Json.parse(prefs.getString(KEY, "[]") ?: "[]") as? List<*>)
            ?.mapNotNull { it as? List<Any?> }
            .orEmpty()

    private companion object {
        const val FILE = "agent_app_launches"
        const val KEY = "launches"
        const val MAX = 200
    }
}

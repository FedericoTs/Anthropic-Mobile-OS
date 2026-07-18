package org.agentnativeos.app

import android.content.Context
import org.agentnativeos.core.memory.TaskStatus
import org.agentnativeos.core.predict.NowContext
import org.agentnativeos.core.predict.Suggestion
import org.agentnativeos.core.predict.SuggestionEngine
import org.agentnativeos.core.predict.SuggestionFeedback
import org.agentnativeos.core.predict.SuggestionFeedbackCodec
import org.agentnativeos.core.predict.UsageEvent
import org.agentnativeos.core.predict.normalizeIntent
import java.time.ZoneId

/**
 * The predictive layer's on-device home: turns completed-task history into calm suggestions
 * for right now, and remembers what was shown / tapped / dismissed so the engine can mute and
 * throttle. All the judgment lives in the pure-core [SuggestionEngine]; this only supplies the
 * device signals (clock, timezone, task memory) and persists feedback. Plain prefs — nothing
 * here is a secret (intents are the user's own words; credentials live encrypted elsewhere).
 */
class PredictionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** User-owned master switch (default on). */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    /** Testing aid: suggest after a single run, no cooldown/mute/throttle (default off). */
    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) { prefs.edit().putBoolean(KEY_DEMO, value).apply() }

    private fun engine(): SuggestionEngine = if (demoMode) SuggestionEngine.demo() else SuggestionEngine()

    /** Suggestions for the current moment, from completed task history. Empty if disabled. */
    fun suggestionsNow(memory: PersistentTaskMemory, nowMs: Long = System.currentTimeMillis()): List<Suggestion> {
        if (!enabled) return emptyList()
        val zone = ZoneId.systemDefault()
        val history = memory.recent(HISTORY)
            .filter { it.status == TaskStatus.COMPLETED }
            .map { UsageEvent.from(it, zone) }
        return engine().suggest(history, NowContext.at(nowMs, zone), feedback())
    }

    fun recordShown(intents: List<String>) {
        if (intents.isEmpty()) return
        var fb = feedback()
        for (intent in intents) fb = updateIntent(fb, intent) { it.copy(shown = it.shown + 1) }
        save(fb.copy(totalShown = fb.totalShown + intents.size))
    }

    fun recordTapped(intent: String) {
        val fb = updateIntent(feedback(), intent) { it.copy(tapped = it.tapped + 1) }
        save(fb.copy(totalTapped = fb.totalTapped + 1))
    }

    fun recordDismissed(intent: String, nowMs: Long = System.currentTimeMillis()) {
        save(updateIntent(feedback(), intent) { it.copy(dismissedAtMs = it.dismissedAtMs + nowMs) })
    }

    private fun feedback(): SuggestionFeedback =
        SuggestionFeedbackCodec.decode(prefs.getString(KEY_FEEDBACK, "{}") ?: "{}")

    private fun save(feedback: SuggestionFeedback) =
        prefs.edit().putString(KEY_FEEDBACK, SuggestionFeedbackCodec.encode(feedback)).apply()

    private fun updateIntent(
        fb: SuggestionFeedback,
        intent: String,
        change: (org.agentnativeos.core.predict.IntentFeedback) -> org.agentnativeos.core.predict.IntentFeedback,
    ): SuggestionFeedback {
        val key = normalizeIntent(intent)
        val current = fb.perIntent[key] ?: org.agentnativeos.core.predict.IntentFeedback()
        return fb.copy(perIntent = fb.perIntent + (key to change(current)))
    }

    private companion object {
        const val FILE = "agent_predict"
        const val KEY_FEEDBACK = "feedback"
        const val KEY_ENABLED = "enabled"
        const val KEY_DEMO = "demo_mode"
        const val HISTORY = 50
    }
}

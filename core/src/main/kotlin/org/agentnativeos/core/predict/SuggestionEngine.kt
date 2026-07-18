package org.agentnativeos.core.predict

/** What the home offers: an intent to re-run (on a TAP — the engine never acts). */
data class Suggestion(val intent: String, val score: Double)

/** Per-intent feedback the engine reads to decide muting (persisted by the app). */
data class IntentFeedback(
    val dismissedAtMs: List<Long> = emptyList(),
    val shown: Int = 0,
    val tapped: Int = 0,
)

/** All suggestion feedback: per-intent state + global totals for the throttle / eval. */
data class SuggestionFeedback(
    val perIntent: Map<String, IntentFeedback> = emptyMap(),
    val totalShown: Int = 0,
    val totalTapped: Int = 0,
) {
    /** Rolling tap-rate; 1.0 before anything has been shown (so we don't throttle cold). */
    fun tapRate(): Double = if (totalShown == 0) 1.0 else totalTapped.toDouble() / totalShown
}

/**
 * Turns mined patterns into at most a couple of CALM suggestions — precision over recall.
 * A suggestion is a thing the user can TAP to run through the normal agent loop (gate and
 * all); this engine only ranks and filters, it never executes anything.
 *
 * Filters, in order: enough support and confidence; not something just done; not muted by
 * feedback (dismissed recently, or shown repeatedly and never tapped); and a global throttle
 * that quiets the whole surface if the user keeps ignoring it (the automated
 * "ambient-correctness" guard — a noisy home that's often wrong is worse than a silent one).
 */
class SuggestionEngine(
    private val miner: PatternMiner = PatternMiner(),
    private val maxSuggestions: Int = 2,
    private val minSupport: Int = 3,
    private val minScore: Double = 1.5,
    private val recentSuppressMs: Long = 3 * HOUR_MS,
    private val dismissMuteMs: Long = 7 * DAY_MS,
    private val ignoredMuteThreshold: Int = 5,
    private val throttleMinShown: Int = 10,
) {
    fun suggest(
        history: List<UsageEvent>,
        now: NowContext,
        feedback: SuggestionFeedback = SuggestionFeedback(),
    ): List<Suggestion> {
        // Global throttle: if the user keeps ignoring suggestions, go quiet.
        val cap = when {
            feedback.totalShown >= throttleMinShown && feedback.tapRate() < 0.10 -> 0
            feedback.totalShown >= throttleMinShown && feedback.tapRate() < 0.20 -> 1
            else -> maxSuggestions
        }
        if (cap == 0) return emptyList()

        val lastDone = history.groupBy { normalizeIntent(it.intent) }
            .mapValues { (_, events) -> events.maxOf { it.atMs } }

        return miner.rank(history, now).asSequence()
            .filter { it.support >= minSupport && it.score >= minScore }
            .filterNot { p ->
                val key = normalizeIntent(p.intent)
                val doneRecently = now.atMs - (lastDone[key] ?: Long.MIN_VALUE / 2) < recentSuppressMs
                doneRecently || muted(key, feedback, now.atMs)
            }
            .take(cap)
            .map { Suggestion(it.intent, it.score) }
            .toList()
    }

    private fun muted(key: String, feedback: SuggestionFeedback, nowMs: Long): Boolean {
        val f = feedback.perIntent[key] ?: return false
        if (f.dismissedAtMs.any { nowMs - it < dismissMuteMs }) return true // dismissed lately
        if (f.shown >= ignoredMuteThreshold && f.tapped == 0) return true // shown a lot, never tapped
        return false
    }

    companion object {
        private const val HOUR_MS = 60L * 60 * 1000
        private const val DAY_MS = 24 * HOUR_MS

        /**
         * Relaxed thresholds so a suggestion appears after a SINGLE run, with no cooldown,
         * mute, or throttle — for demoing the "Right now" surface on a device that has no
         * habit history yet. NOT the production policy (which is calm/precision-first).
         */
        fun demo(): SuggestionEngine = SuggestionEngine(
            minSupport = 1,
            minScore = 0.0,
            recentSuppressMs = 0L,
            ignoredMuteThreshold = Int.MAX_VALUE,
            throttleMinShown = Int.MAX_VALUE,
        )
    }
}

package org.agentnativeos.core.predict

import kotlin.math.pow

/** A recurring habit the miner found: an intent, its recency-weighted score, and how many times seen. */
data class Pattern(val intent: String, val score: Double, val support: Int)

/**
 * Finds what the user tends to do in the CURRENT time-slice (bucket × weekday/weekend),
 * scored by recency so old habits fade. Pure arithmetic over [UsageEvent]s — no model call.
 *
 * Score = Σ over matching past events of decay^(ageDays / halfLife); a habit done 3 recent
 * mornings outranks one done twice months ago. Only events in the same bucket AND day-class
 * as "now" count, so a 9am-timer habit surfaces at 9am on a weekday, not at 9pm or on Sunday.
 */
class PatternMiner(private val halfLifeDays: Double = 14.0) {

    fun rank(history: List<UsageEvent>, now: NowContext): List<Pattern> {
        val grouped = LinkedHashMap<String, MutableList<UsageEvent>>()
        for (e in history) {
            if (e.dayClass != now.dayClass) continue
            if (e.bucket != now.bucket) continue
            grouped.getOrPut(normalizeIntent(e.intent)) { mutableListOf() }.add(e)
        }
        return grouped.values
            .map { events ->
                val score = events.sumOf { decayWeight(now.atMs - it.atMs) }
                // Re-offer the most recent phrasing of the habit.
                val representative = events.maxByOrNull { it.atMs }!!.intent
                Pattern(representative, score, events.size)
            }
            .sortedByDescending { it.score }
    }

    private fun decayWeight(ageMs: Long): Double {
        val ageDays = (ageMs.coerceAtLeast(0)).toDouble() / MS_PER_DAY
        return 0.5.pow(ageDays / halfLifeDays)
    }

    private companion object {
        const val MS_PER_DAY = 24.0 * 60 * 60 * 1000
    }
}

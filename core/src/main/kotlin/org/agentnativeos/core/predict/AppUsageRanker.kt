package org.agentnativeos.core.predict

/**
 * Ranks APPS for the home's "Apps, right now" row — the launcher shelf that adapts to
 * your routine. Input events carry the package name in [UsageEvent.intent].
 *
 * Ranking is two-tiered so the row is useful from day one and habitual over time:
 *  1. time-sliced habits first — apps you tend to open in the CURRENT bucket × day-class
 *     (the same recency-weighted mining as intent suggestions);
 *  2. then overall recency — most recently opened apps regardless of slice — to fill the
 *     remaining slots (a shelf with empty slots helps nobody).
 * Like everything predictive here: pure arithmetic, on-device, and it only RANKS — a tap
 * launches; nothing opens on its own.
 */
class AppUsageRanker(private val miner: PatternMiner = PatternMiner()) {

    fun rank(events: List<UsageEvent>, now: NowContext, limit: Int = 5): List<String> {
        val habitual = miner.rank(events, now).map { normalizeIntent(it.intent) }
        val recent = events.sortedByDescending { it.atMs }.map { normalizeIntent(it.intent) }
        return (habitual + recent).distinct().take(limit)
    }
}

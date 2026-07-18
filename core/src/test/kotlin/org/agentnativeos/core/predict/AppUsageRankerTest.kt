package org.agentnativeos.core.predict

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageRankerTest {

    private val now = 1_700_000_000_000L
    private fun open(pkg: String, daysAgo: Long, hour: Int, day: DayClass = DayClass.WEEKDAY) =
        UsageEvent(pkg, now - daysAgo * 86_400_000L, hour, day)
    private val ranker = AppUsageRanker()
    private fun morning() = NowContext(now, 9, DayClass.WEEKDAY)

    @Test
    fun habitualAppsForThisTimeSliceRankAheadOfMerelyRecentOnes() {
        val events = listOf(
            // com.calendar is the weekday-morning habit (3 opens in-bucket).
            open("com.calendar", 1, 9), open("com.calendar", 2, 9), open("com.calendar", 3, 9),
            // com.game was opened more recently, but in the evening.
            open("com.game", 0, 20),
        )
        assertEquals(listOf("com.calendar", "com.game"), ranker.rank(events, morning()))
    }

    @Test
    fun recencyFillsTheRemainingSlotsWithoutDuplicates() {
        val events = listOf(
            open("com.calendar", 1, 9), open("com.calendar", 2, 9), open("com.calendar", 3, 9),
            open("com.maps", 0, 20), open("com.mail", 1, 20), open("com.maps", 2, 20),
        )
        // Habit first, then by recency (maps opened today, mail yesterday) — no repeats.
        assertEquals(listOf("com.calendar", "com.maps", "com.mail"), ranker.rank(events, morning()))
    }

    @Test
    fun capsAtTheRequestedLimit() {
        val events = (1..8).map { open("com.app$it", it.toLong(), 9) }
        assertEquals(5, ranker.rank(events, morning(), limit = 5).size)
    }

    @Test
    fun emptyHistoryRanksNothing() {
        assertEquals(emptyList<String>(), ranker.rank(emptyList(), morning()))
    }
}

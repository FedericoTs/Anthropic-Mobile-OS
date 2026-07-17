package org.agentnativeos.core.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternMinerTest {

    private val now = 1_700_000_000_000L
    private fun ev(intent: String, daysAgo: Long, hour: Int, day: DayClass) =
        UsageEvent(intent, now - daysAgo * 86_400_000L, hour, day)

    @Test
    fun surfacesAMorningHabitAtMorningOnWeekdaysOnly() {
        val history = listOf(
            ev("start a 25 minute timer", 1, 9, DayClass.WEEKDAY),
            ev("start a 25 minute timer", 2, 9, DayClass.WEEKDAY),
            ev("start a 25 minute timer", 3, 9, DayClass.WEEKDAY),
            ev("check the weather", 1, 20, DayClass.WEEKDAY), // evening noise, different bucket
        )
        val miner = PatternMiner()

        val morning = miner.rank(history, NowContext(now, 9, DayClass.WEEKDAY))
        assertEquals("start a 25 minute timer", morning.first().intent)
        assertEquals(3, morning.first().support)

        // Wrong bucket: the timer habit does not surface in the evening.
        assertTrue(miner.rank(history, NowContext(now, 20, DayClass.WEEKDAY)).none { it.intent.contains("timer") })
        // Wrong day-class: nothing weekday-morning surfaces on a weekend morning.
        assertTrue(miner.rank(history, NowContext(now, 9, DayClass.WEEKEND)).isEmpty())
    }

    @Test
    fun recencyOutweighsAnOlderMoreFrequentHabit() {
        val history = listOf(
            ev("recent thing", 0, 9, DayClass.WEEKDAY),
            ev("recent thing", 1, 9, DayClass.WEEKDAY),
            ev("old thing", 40, 9, DayClass.WEEKDAY),
            ev("old thing", 41, 9, DayClass.WEEKDAY),
            ev("old thing", 42, 9, DayClass.WEEKDAY),
        )
        val ranked = PatternMiner(halfLifeDays = 14.0).rank(history, NowContext(now, 9, DayClass.WEEKDAY))
        // 2 recent (barely decayed) beat 3 old (decayed over ~3 half-lives).
        assertEquals("recent thing", ranked.first().intent)
    }

    @Test
    fun groupsIntentsByNormalizedText() {
        val history = listOf(
            ev("Set a timer", 1, 9, DayClass.WEEKDAY),
            ev("set a  timer.", 2, 9, DayClass.WEEKDAY),
        )
        val ranked = PatternMiner().rank(history, NowContext(now, 9, DayClass.WEEKDAY))
        assertEquals(1, ranked.size)
        assertEquals(2, ranked.first().support) // both variants pooled
    }
}

package org.agentnativeos.core.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    private val now = 1_700_000_000_000L
    private fun ev(intent: String, daysAgo: Long, hour: Int, day: DayClass) =
        UsageEvent(intent, now - daysAgo * 86_400_000L, hour, day)
    private fun timerHistory(n: Int) = (1..n).map { ev("start a 25 minute timer", it.toLong(), 9, DayClass.WEEKDAY) }
    private fun weatherHistory(n: Int) = (1..n).map { ev("check the weather", it.toLong(), 9, DayClass.WEEKDAY) }
    private fun morningNow() = NowContext(now, 9, DayClass.WEEKDAY)
    private val engine = SuggestionEngine()

    @Test
    fun suggestsAHabitThatClearsSupportAndConfidence() {
        val s = engine.suggest(timerHistory(3), morningNow())
        assertEquals(1, s.size)
        assertEquals("start a 25 minute timer", s.first().intent)
        assertTrue(s.first().score > 0)
    }

    @Test
    fun doesNotSuggestBelowMinimumSupport() {
        assertTrue(engine.suggest(timerHistory(2), morningNow()).isEmpty()) // only done twice
    }

    @Test
    fun suppressesSomethingJustDone() {
        val history = timerHistory(3) +
            UsageEvent("start a 25 minute timer", now - 60 * 60 * 1000L, 9, DayClass.WEEKDAY) // 1h ago
        assertTrue(engine.suggest(history, morningNow()).isEmpty())
    }

    @Test
    fun mutesADismissedSuggestionThenReturnsAfterTheWindow() {
        val key = normalizeIntent("start a 25 minute timer")
        val justDismissed = SuggestionFeedback(
            perIntent = mapOf(key to IntentFeedback(dismissedAtMs = listOf(now - 86_400_000L))), // yesterday
        )
        assertTrue(engine.suggest(timerHistory(3), morningNow(), justDismissed).isEmpty())

        val longAgo = SuggestionFeedback(
            perIntent = mapOf(key to IntentFeedback(dismissedAtMs = listOf(now - 10 * 86_400_000L))), // 10 days > 7
        )
        assertEquals(1, engine.suggest(timerHistory(3), morningNow(), longAgo).size)
    }

    @Test
    fun mutesASuggestionShownRepeatedlyButNeverTapped() {
        val key = normalizeIntent("start a 25 minute timer")
        val ignored = SuggestionFeedback(perIntent = mapOf(key to IntentFeedback(shown = 5, tapped = 0)))
        assertTrue(engine.suggest(timerHistory(3), morningNow(), ignored).isEmpty())
    }

    @Test
    fun globalThrottleQuietsTheSurfaceWhenIgnored() {
        val history = timerHistory(3) + weatherHistory(3) // two morning-weekday habits
        assertEquals("both surface with no bad feedback", 2, engine.suggest(history, morningNow()).size)

        // tapRate 1/20 = .05 < .10 -> go silent
        val silent = SuggestionFeedback(totalShown = 20, totalTapped = 1)
        assertTrue(engine.suggest(history, morningNow(), silent).isEmpty())

        // tapRate 3/20 = .15 < .20 -> cap to one
        val quiet = SuggestionFeedback(totalShown = 20, totalTapped = 3)
        assertEquals(1, engine.suggest(history, morningNow(), quiet).size)
    }

    @Test
    fun capsAtTwoSuggestions() {
        val history = timerHistory(3) + weatherHistory(3) + (1..3).map {
            ev("open the calendar", it.toLong(), 9, DayClass.WEEKDAY)
        }
        assertEquals(2, engine.suggest(history, morningNow()).size) // three habits, at most two shown
    }

    @Test
    fun suggestionsAreInertData_theEngineNeverActs() {
        // The only output is Suggestion(intent, score) — plain data. There is no Actuator or
        // Perceiver anywhere in this package, so nothing runs until the UI hands the intent to
        // the agent loop on a user tap. This pins that contract.
        val s = engine.suggest(timerHistory(3), morningNow()).first()
        assertEquals("start a 25 minute timer", s.intent)
    }
}

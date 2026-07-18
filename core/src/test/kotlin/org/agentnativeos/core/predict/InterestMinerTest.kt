package org.agentnativeos.core.predict

import org.agentnativeos.core.memory.TaskRecord
import org.agentnativeos.core.memory.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestMinerTest {

    private val now = 1_700_000_000_000L
    private fun task(intent: String, daysAgo: Long) =
        TaskRecord(intent, TaskStatus.COMPLETED, "ok", 1, now - daysAgo * 86_400_000L)

    @Test
    fun recurringTopicsSurfaceFunctionWordsDoNot() {
        val interests = InterestMiner.topInterests(
            listOf(
                task("find a pizza place for tonight", 1),
                task("order a pizza margherita", 3),
                task("set a timer for the pizza oven", 5),
                task("open the calendar", 2), // "open"/"the" are stopwords; calendar seen once
            ),
            now,
        )
        assertTrue("pizza recurs -> an interest", "pizza" in interests)
        assertTrue("stopwords never become interests", interests.none { it in setOf("the", "for", "open", "find") })
        assertTrue("a single mention is not an interest", "calendar" !in interests)
    }

    @Test
    fun staleTopicsDecayAwayRecentOnesRank() {
        val interests = InterestMiner.topInterests(
            listOf(
                task("pizza tonight", 1), task("pizza again", 2),
                task("sushi order", 200), task("sushi again", 210), task("sushi once more", 220),
            ),
            now,
        )
        assertTrue("recent pizza qualifies", "pizza" in interests)
        assertTrue("200-day-old sushi has decayed below threshold", "sushi" !in interests)
    }

    @Test
    fun italianIntentsMineCleanly() {
        val interests = InterestMiner.topInterests(
            listOf(
                task("cerca una pizzeria vicino a me", 1),
                task("trova la pizzeria migliore", 2),
            ),
            now,
        )
        assertEquals(listOf("pizzeria"), interests.filter { it == "pizzeria" })
        assertTrue(interests.none { it in setOf("una", "cerca", "trova") })
    }

    @Test
    fun emptyHistoryMeansNoProfile() {
        assertEquals(emptyList<String>(), InterestMiner.topInterests(emptyList(), now))
    }
}

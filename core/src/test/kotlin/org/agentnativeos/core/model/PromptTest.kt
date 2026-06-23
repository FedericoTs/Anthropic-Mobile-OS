package org.agentnativeos.core.model

import org.agentnativeos.core.memory.TaskRecord
import org.agentnativeos.core.memory.TaskStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun systemPromptTellsThePlannerMemoryDoesNotCompleteTheCurrentIntent() {
        // Regression: a prior run's record in memory was read as "already done", so the
        // agent returned done at step 0 without acting. The rule must say history never
        // satisfies the current intent and to judge done from the live screen.
        // Normalize wrapping (the prompt is built line-by-line) so phrases that span a
        // line break still match.
        val system = Prompt.system().lowercase().replace(Regex("\\s+"), " ")
        assertTrue("must frame earlier tasks as history", system.contains("history"))
        assertTrue(
            "must say history does not satisfy the current intent",
            system.contains("does not satisfy the current intent"),
        )
        assertTrue(
            "must forbid citing a previous run to report done on step one",
            system.contains("never reply done on the first step"),
        )
    }

    @Test
    fun userPromptFramesRecentTasksAsHistoryNotCompletion() {
        val ctx = PlanningContext(
            intent = "send an email to a@b.com",
            untrustedScreen = "(screen)",
            stepIndex = 0,
            recentTasks = listOf(
                TaskRecord("send an email to a@b.com", TaskStatus.COMPLETED, "sent", 6, 0L),
            ),
        )
        val user = Prompt.user(ctx)
        // The header must NOT read as "tasks you have done" (which the model treated as
        // completing the new request) — it must mark them history that doesn't complete it.
        assertTrue("recent tasks must be framed as history only", user.contains("history only"))
        assertTrue(user.contains("do NOT complete the current Intent"))
        // The record itself is still shown for continuity.
        assertTrue(user.contains("send an email to a@b.com"))
    }
}

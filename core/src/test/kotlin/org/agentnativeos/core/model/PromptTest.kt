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
    fun systemPromptSaysInvokingAComposeCapabilityIsNotSendingAndBlankIsNotDone() {
        // Regression: the agent invoked send_email (which only OPENS a draft) and
        // reported done without tapping Send; and it hallucinated done from a blank
        // (still-loading) screen.
        val system = Prompt.system().lowercase().replace(Regex("\\s+"), " ")
        assertTrue("invoking must be framed as opening a draft, not sending", system.contains("only opens a pre-filled draft"))
        assertTrue("must require tapping send/save to finish", system.contains("does not") && system.contains("send or save"))
        assertTrue("must forbid done at the invoke", system.contains("never report done merely because you invoked"))
        assertTrue("a blank screen is not done", system.contains("never report done from a blank screen"))
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

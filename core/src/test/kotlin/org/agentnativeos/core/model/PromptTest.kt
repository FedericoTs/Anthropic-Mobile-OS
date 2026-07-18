package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.memory.TaskRecord
import org.agentnativeos.core.memory.TaskStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun verifyPromptIsSkepticalAndJudgesTheScreenOnly() {
        val sys = Prompt.verifySystem().lowercase().replace(Regex("\\s+"), " ")
        assertTrue("is a strict verifier", sys.contains("strict verifier"))
        assertTrue("judges the current screen only", sys.contains("from the current screen only"))
        assertTrue("the model's summary/memory is not evidence", sys.contains("are not evidence"))
        assertTrue("a past run never counts", sys.contains("never a valid reason"))
        assertTrue("a question is done once answered", sys.contains("if the intent is a question"))
        assertTrue("an action with no steps is not done", sys.contains("claims an action is done, it is not done"))
        assertTrue("a still-open compose/draft is not done", sys.contains("draft means it is not done"))
        assertTrue("emits a verified verdict", sys.contains("\"verified\""))

        val user = Prompt.verifyUser(
            PlanningContext(
                intent = "send an email to a@b.com",
                untrustedScreen = "(screen)",
                stepIndex = 3,
                history = listOf(AgentAction.Tap("Invia")),
            ),
            "sent the email",
        )
        assertTrue(user.contains("send an email to a@b.com"))
        assertTrue(user.contains("claims it is DONE"))
        assertTrue("passes the model's own claim to the verifier", user.contains("sent the email"))
    }

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
        assertTrue("must forbid done at the invoke", system.contains("merely invoking the capability"))
        assertTrue("a blank screen is not done", system.contains("never report done from a blank screen"))
    }

    @Test
    fun systemPromptForbidsRelaunchAfterComposeAndRequiresSendThisRun() {
        // Regression: the agent relaunched Gmail by package after invoking send_email
        // (abandoning the draft), and it declared a send done without executing the send.
        val system = Prompt.system().lowercase().replace(Regex("\\s+"), " ")
        assertTrue(
            "must forbid relaunching the app by package (abandons the draft)",
            system.contains("do not relaunch that app by package"),
        )
        assertTrue(
            "sending requires executing the send tap this run",
            system.contains("sent a message only after you tapped its send button in this run"),
        )
    }

    @Test
    fun decomposePromptDemandsSelfContainedGoalsAndAllowsNoForcedSplit() {
        val sys = Prompt.decomposeSystem().lowercase().replace(Regex("\\s+"), " ")
        assertTrue("caps the fan-out", sys.contains("at most 3"))
        assertTrue("each goal must stand alone", sys.contains("self-contained"))
        assertTrue("single tasks stay single", sys.contains("return exactly one goal"))
        assertTrue("emits a goals object", sys.contains("\"goals\""))
        assertTrue(Prompt.decomposeUser("find pizza and text Marco").contains("find pizza and text Marco"))
    }

    @Test
    fun compoundHeuristicCatchesConnectivesWithoutOvertriggering() {
        assertTrue(Compound.looksCompound("find a pizzeria nearby and text the address to Marco"))
        assertTrue(Compound.looksCompound("apri gmail e poi imposta un timer"))
        assertTrue(Compound.looksCompound("set a timer; open maps"))
        assertTrue(!Compound.looksCompound("set a 5 minute timer"))
        assertTrue(!Compound.looksCompound("manda una mail a Marco"))
    }

    @Test
    fun outputRuleAsksForStructuredPlacesOnlyFromTheScreen() {
        val sys = Prompt.system().lowercase().replace(Regex("\\s+"), " ")
        assertTrue("structured places form is specified", sys.contains("\"places\""))
        assertTrue("only real, perceived places", sys.contains("never invented"))
        assertTrue("optional — omitted otherwise", sys.contains("otherwise omit \"places\""))
    }

    @Test
    fun interestsArePersonalizationContextNeverAuthority() {
        val ctx = PlanningContext(
            intent = "find a pizzeria nearby and tell me about it",
            untrustedScreen = "(screen)",
            stepIndex = 0,
            userInterests = listOf("pizza", "vegetarian", "jazz"),
        )
        val user = Prompt.user(ctx).lowercase().replace(Regex("\\s+"), " ")
        assertTrue("profile is rendered", user.contains("pizza, vegetarian, jazz"))
        assertTrue("framed as personalization", user.contains("personalize"))
        assertTrue("never overrides the intent", user.contains("never to change or add to the intent"))
        // No interests -> no profile block at all.
        assertTrue(!Prompt.user(ctx.copy(userInterests = emptyList())).contains("recurring interests"))
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

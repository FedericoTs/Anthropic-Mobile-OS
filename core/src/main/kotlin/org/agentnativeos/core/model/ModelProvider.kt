package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.memory.TaskRecord

/**
 * How we authenticate to the model: one interface, two credential modes. The
 * real network impl lives in the Android app; the brain only knows the seam.
 */
sealed interface AuthMode {
    data class SubscriptionOAuth(val accessToken: String) : AuthMode
    data class ApiKey(val key: String) : AuthMode
}

/**
 * A model identity. Pin an EQUIVALENT model on both auth paths so a swap doesn't
 * visibly change planning behavior and undercut "it's just swappable".
 */
data class ModelId(val name: String) {
    companion object {
        /** Cheapest model — also the default planner (see below) and the live-test model. */
        val CHEAPEST = ModelId("claude-haiku-4-5")

        /**
         * Production default planner. The cheapest model, so casual use stays
         * inexpensive; the user can pick a stronger one per activity in Settings
         * (see [ModelCatalog]).
         */
        val DEFAULT = CHEAPEST
    }
}

/** A launchable app the agent can open directly by package (perceived from the device). */
data class AppInfo(val label: String, val packageName: String)

/** A direct device capability the agent can invoke instead of driving the UI. */
data class Capability(val name: String, val description: String, val params: List<String> = emptyList())

/** What the planner sees each step: the intent + the screen as untrusted data. */
data class PlanningContext(
    val intent: String,
    val untrustedScreen: String,
    val stepIndex: Int,
    val history: List<AgentAction> = emptyList(),
    val availableApps: List<AppInfo> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
    /** Recent tasks this agent has done (newest first) — continuity + "what did you do?". */
    val recentTasks: List<TaskRecord> = emptyList(),
    /** The previous attempt's failure, if any, so the model can try something else. */
    val lastError: String? = null,
)

/** A second opinion on a claimed completion: is the task ACTUALLY done on the live screen? */
data class VerifyResult(val verified: Boolean, val reason: String = "")

/** The planner seam: given context, return the next typed action. */
fun interface ModelProvider {
    fun nextAction(context: PlanningContext): AgentAction

    /**
     * Verify a claimed completion against the current screen before the loop accepts
     * it. The model has repeatedly declared "done" it cannot see (memory poisoning /
     * an unsent draft), so the real provider overrides this with a skeptical check.
     * Default = accept, so scripted/lambda providers (tests, the eval) don't
     * second-guess themselves. [claimedSummary] is the model's own "done" text.
     */
    fun verify(context: PlanningContext, claimedSummary: String): VerifyResult = VerifyResult(true)
}

/**
 * Deterministic provider for tests and the scripted-intent eval: plays a fixed
 * sequence of actions, then Done. Never touches the network.
 */
class ScriptedModelProvider(private val script: List<AgentAction>) : ModelProvider {
    private var index = 0
    override fun nextAction(context: PlanningContext): AgentAction =
        if (index < script.size) script[index++] else AgentAction.Done("script complete")
}

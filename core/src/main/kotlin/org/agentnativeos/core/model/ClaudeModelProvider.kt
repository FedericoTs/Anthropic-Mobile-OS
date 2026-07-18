package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction

/**
 * Real planner backed by the Anthropic Messages API. One interface, two auth
 * modes: a Claude subscription OAuth bearer token, or a user-supplied API key.
 *
 * Pure JVM (the HTTP + JSON live in [AnthropicClient]/[Json]), so the live
 * round-trip is unit-testable — gated on a key — rather than only reachable on a
 * device. Failures are non-fatal at the loop level: a timeout / bad key /
 * unparseable reply returns [AgentAction.Abort], which the loop turns into a
 * transparent handoff rather than a crash.
 */
class ClaudeModelProvider(
    private val client: AnthropicClient,
    private val maxParseAttempts: Int = 2,
) : ModelProvider {

    /** Convenience: build the default (real-network) client for the given auth. */
    constructor(
        auth: AuthMode,
        model: ModelId = ModelId.DEFAULT,
        endpoint: String = "https://api.anthropic.com/v1/messages",
    ) : this(AnthropicClient(auth, model, endpoint))

    override fun nextAction(context: PlanningContext): AgentAction {
        var lastRaw = ""
        var reminder = ""
        repeat(maxParseAttempts) {
            val raw = try {
                client.complete(Prompt.system(), Prompt.user(context) + reminder)
            } catch (t: Throwable) {
                return AgentAction.Abort("model unreachable: ${t.message}")
            }
            ActionJson.parse(raw)?.let { return it }
            // The model replied with prose instead of one JSON action — re-ask firmly
            // rather than aborting the whole run on a single malformed turn.
            lastRaw = raw
            reminder = "\n\nIMPORTANT: your previous reply was not accepted. Reply with EXACTLY ONE " +
                "JSON action object and nothing else — no prose, no markdown."
        }
        return AgentAction.Abort("couldn't parse model output: ${lastRaw.trim().take(200)}")
    }

    override fun decompose(intent: String): List<String> {
        val raw = try {
            client.complete(Prompt.decomposeSystem(), Prompt.decomposeUser(intent))
        } catch (t: Throwable) {
            return listOf(intent) // fail safe: the single-agent loop handles it
        }
        return ActionJson.parseGoals(raw) ?: listOf(intent)
    }

    override fun verify(context: PlanningContext, claimedSummary: String): VerifyResult {
        repeat(maxParseAttempts) {
            val raw = try {
                client.complete(Prompt.verifySystem(), Prompt.verifyUser(context, claimedSummary))
            } catch (t: Throwable) {
                // A verifier that can't run must never block a genuine completion — accept.
                return VerifyResult(true, "verify unavailable: ${t.message}")
            }
            ActionJson.parseVerdict(raw)?.let { return it }
        }
        // Unreadable verdict after retries: fail OPEN so a flaky checker can't loop forever.
        return VerifyResult(true, "verify unreadable")
    }
}

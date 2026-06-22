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
class ClaudeModelProvider(private val client: AnthropicClient) : ModelProvider {

    /** Convenience: build the default (real-network) client for the given auth. */
    constructor(
        auth: AuthMode,
        model: ModelId = ModelId.DEFAULT,
        endpoint: String = "https://api.anthropic.com/v1/messages",
    ) : this(AnthropicClient(auth, model, endpoint))

    override fun nextAction(context: PlanningContext): AgentAction =
        try {
            ActionJson.parse(client.complete(Prompt.system(), Prompt.user(context)))
                ?: AgentAction.Abort("couldn't parse model output")
        } catch (t: Throwable) {
            AgentAction.Abort("model unreachable: ${t.message}")
        }
}

package os.amos.shell.ai

/**
 * Provider-agnostic conversation types. Nothing outside the [ai] package should
 * depend on a specific backend (API, subscription, mock) — everything talks to
 * the [ClaudeProvider] interface using these models.
 */

enum class Role { USER, ASSISTANT, SYSTEM }

/** One turn in a conversation with Claude. */
data class ChatMessage(
    val role: Role,
    val text: String,
)

/**
 * A tool the model may call. In AMOS, every tool maps onto a Capability in the
 * Capability Bus (see the `capability` package), which is how the agent acts on
 * the device.
 *
 * @param inputSchemaJson a JSON Schema object describing the tool's input.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/** A tool invocation requested by the model. */
data class ToolCall(
    val id: String,
    val name: String,
    /** Raw JSON object of arguments, as produced by the model. */
    val argumentsJson: String,
)

/** The provider's response to a single turn. */
data class ProviderReply(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    /** e.g. "end_turn", "tool_use", "max_tokens", "refusal". */
    val stopReason: String? = null,
)

/** Which Claude backend to talk to. */
enum class ProviderKind { ANTHROPIC_API, SUBSCRIPTION, MOCK }

data class ProviderConfig(
    val kind: ProviderKind,
    val apiKey: String = "",
    /**
     * Default Claude model. Chosen as a balance of latency and capability for an
     * on-device assistant; override in Settings. Keep in sync with the values in
     * docs/ARCHITECTURE.md.
     */
    val model: String = "claude-sonnet-4-6",
    val maxTokens: Int = 1024,
)

package os.amos.shell.ai

/**
 * The single seam between AMOS and Claude.
 *
 * The agentic shell and the Agent Runtime depend only on this interface, so a
 * new backend (e.g. a future subscription transport) can be added without
 * touching any UI or agent code — see [ProviderFactory].
 */
interface ClaudeProvider {

    val kind: ProviderKind

    /** Human-readable, for Settings ("Anthropic API", "Offline (Mock)", …). */
    val displayName: String

    /**
     * Run one model turn over [messages], optionally exposing [tools].
     *
     * Implementations must be safe to call from a background coroutine
     * (`Dispatchers.IO`) and must translate transport/auth failures into a
     * [ProviderException] rather than leaking backend-specific exceptions.
     *
     * @param system optional system prompt establishing the agent's behaviour.
     */
    suspend fun send(
        messages: List<ChatMessage>,
        tools: List<ToolSpec> = emptyList(),
        system: String? = null,
    ): ProviderReply
}

/** Uniform failure type so callers don't depend on a specific HTTP/IO stack. */
class ProviderException(
    message: String,
    cause: Throwable? = null,
    /** HTTP status when applicable, else null. */
    val status: Int? = null,
) : Exception(message, cause)

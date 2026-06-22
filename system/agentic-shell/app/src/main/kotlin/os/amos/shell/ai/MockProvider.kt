package os.amos.shell.ai

import kotlinx.coroutines.delay

/**
 * Fully offline provider so AMOS boots and is explorable with zero configuration.
 * Deterministic, no network, no key. Selected automatically when no API key is
 * present (see [ProviderFactory]).
 *
 * It intentionally demonstrates the agentic surface: when the user clearly asks
 * to open an app, it emits a `launch_app` tool call so the Capability Bus path
 * can be exercised without a live model.
 */
class MockProvider : ClaudeProvider {

    override val kind = ProviderKind.MOCK
    override val displayName = "Offline (Mock)"

    override suspend fun send(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        system: String?,
    ): ProviderReply {
        delay(350) // mimic a little latency so the UI feels real
        val last = messages.lastOrNull { it.role == Role.USER }?.text?.trim().orEmpty()
        val lower = last.lowercase()

        val launchTool = tools.firstOrNull { it.name == "launch_app" }
        val openMatch = Regex("""\b(open|launch|start)\s+(.+)""").find(lower)
        if (launchTool != null && openMatch != null) {
            val appName = openMatch.groupValues[2].trim().trimEnd('.', '!', '?')
            return ProviderReply(
                text = "Opening $appName.",
                toolCalls = listOf(
                    ToolCall(
                        id = "mock_${System.nanoTime()}",
                        name = "launch_app",
                        argumentsJson = """{"query":"${appName.replace("\"", "")}"}""",
                    ),
                ),
                stopReason = "tool_use",
            )
        }

        val reply = when {
            last.isEmpty() ->
                "I'm AMOS — your agentic home. Tell me what you'd like to do, or try \"open settings\"."
            lower.startsWith("hi") || lower.startsWith("hello") ->
                "Hi. I'm running in offline Mock mode — add an Anthropic API key in Settings to think for real. What can I do for you?"
            "?" in last ->
                "Good question. In Mock mode I can't reason live, but with an API key I'd plan this out and use the device's capabilities to do it."
            else ->
                "(Mock) I heard: \"$last\". Add an Anthropic API key to enable real agentic responses."
        }
        return ProviderReply(text = reply, stopReason = "end_turn")
    }
}

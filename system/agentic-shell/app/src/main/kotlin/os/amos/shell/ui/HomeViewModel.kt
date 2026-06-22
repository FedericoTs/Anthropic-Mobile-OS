package os.amos.shell.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.amos.shell.BuildConfig
import os.amos.shell.ai.ChatMessage
import os.amos.shell.ai.ClaudeProvider
import os.amos.shell.ai.ProviderException
import os.amos.shell.ai.ProviderFactory
import os.amos.shell.ai.Role
import os.amos.shell.apps.AppRepository
import os.amos.shell.capability.BuiltInCapabilities
import os.amos.shell.capability.CapabilityBus

/** One bubble in the conversational home. */
data class UiMessage(
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    /** Tool/agent activity note rendered distinctly from chat (transparent agency). */
    val isActivity: Boolean = false,
)

/**
 * Drives the conversational launcher: holds the transcript, talks to the active
 * [ClaudeProvider], and runs the agentic loop — model turn → execute the tool
 * calls it requested via the [CapabilityBus] → feed results back → repeat.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val appRepository = AppRepository(app)
    private val capabilityBus = CapabilityBus(BuiltInCapabilities.all(appRepository))
    private val provider: ClaudeProvider =
        ProviderFactory.create(ProviderFactory.defaultConfig(BuildConfig.ANTHROPIC_API_KEY))

    /** Full alternating user/assistant history sent to the model each turn. */
    private val history = mutableListOf<ChatMessage>()

    private val _messages = MutableStateFlow<List<UiMessage>>(
        listOf(UiMessage(fromUser = false, text = GREETING)),
    )
    val messages = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val providerName: String get() = provider.displayName

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _busy.value) return

        append(UiMessage(fromUser = true, text = text))
        history += ChatMessage(Role.USER, text)
        _busy.value = true

        viewModelScope.launch {
            try {
                runAgentLoop()
            } catch (e: ProviderException) {
                append(UiMessage(fromUser = false, text = e.message ?: "Something went wrong.", isError = true))
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun runAgentLoop() {
        var step = 0
        while (step++ < MAX_STEPS) {
            val reply = provider.send(
                messages = history,
                tools = capabilityBus.toolSpecs(),
                system = SYSTEM_PROMPT,
            )

            if (reply.text.isNotBlank()) {
                append(UiMessage(fromUser = false, text = reply.text))
                history += ChatMessage(Role.ASSISTANT, reply.text)
            }

            if (reply.toolCalls.isEmpty()) return

            // Execute each requested capability and surface it as an activity note.
            // Phase-1 simplification: results are fed back as a user turn rather
            // than as structured tool_result blocks (see docs/ARCHITECTURE.md).
            val resultLines = reply.toolCalls.map { call ->
                val result = capabilityBus.execute(getApplication(), call)
                append(UiMessage(fromUser = false, text = result.summary, isActivity = true))
                "${call.name}: ${result.summary}"
            }
            history += ChatMessage(Role.USER, "Tool results:\n" + resultLines.joinToString("\n"))
        }
    }

    private fun append(message: UiMessage) {
        _messages.value = _messages.value + message
    }

    companion object {
        private const val MAX_STEPS = 5
        private const val GREETING =
            "I'm AMOS. Tell me what you want to do — \"open Maps\", \"search for a recipe\", " +
                "\"call 555-0142\" — and I'll handle it."
        private val SYSTEM_PROMPT = """
            You are AMOS, the agentic layer of a mobile operating system. You ARE the
            home screen, not an app. Understand the user's intent and act on the
            device using the provided tools rather than just describing what to do.
            Prefer taking action with a tool when the request maps to one. Keep
            spoken replies short and natural. Every action you take is shown to the
            user, so be transparent about what you did.
        """.trimIndent()
    }
}

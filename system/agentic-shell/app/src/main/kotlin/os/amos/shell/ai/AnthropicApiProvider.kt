package os.amos.shell.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the official Anthropic Messages API over HTTPS.
 *
 * Why hand-rolled HTTP instead of an SDK: the official Anthropic SDK for the JVM
 * is server-oriented and its transitive dependencies are a known source of
 * runtime breakage on Android. OkHttp + the platform's bundled `org.json` are
 * first-class on Android with zero compatibility risk, so the on-device client
 * speaks the wire protocol directly.
 *
 * API shape (stable): POST https://api.anthropic.com/v1/messages with the
 * `x-api-key` and `anthropic-version: 2023-06-01` headers.
 */
class AnthropicApiProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient = defaultClient(),
) : ClaudeProvider {

    override val kind = ProviderKind.ANTHROPIC_API
    override val displayName = "Anthropic API"

    override suspend fun send(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        system: String?,
    ): ProviderReply = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            throw ProviderException("No Anthropic API key configured.", status = 401)
        }

        val body = buildRequestJson(messages, tools, system).toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(MESSAGES_ENDPOINT)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ProviderException(
                        message = extractErrorMessage(payload) ?: "HTTP ${response.code}",
                        status = response.code,
                    )
                }
                parseReply(payload)
            }
        } catch (e: IOException) {
            throw ProviderException("Network error talking to Claude.", cause = e)
        }
    }

    private fun buildRequestJson(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        system: String?,
    ): JSONObject = JSONObject().apply {
        put("model", config.model)
        put("max_tokens", config.maxTokens)
        if (!system.isNullOrBlank()) put("system", system)

        val msgArray = JSONArray()
        for (m in messages) {
            // The API only accepts user/assistant turns in `messages`; the system
            // prompt is the top-level `system` field above. SYSTEM-role entries
            // are folded into the user turn so the model still sees them.
            val role = when (m.role) {
                Role.ASSISTANT -> "assistant"
                else -> "user"
            }
            msgArray.put(JSONObject().put("role", role).put("content", m.text))
        }
        put("messages", msgArray)

        if (tools.isNotEmpty()) {
            val toolArray = JSONArray()
            for (t in tools) {
                toolArray.put(
                    JSONObject()
                        .put("name", t.name)
                        .put("description", t.description)
                        .put("input_schema", JSONObject(t.inputSchemaJson)),
                )
            }
            put("tools", toolArray)
        }
    }

    private fun parseReply(payload: String): ProviderReply {
        val root = JSONObject(payload)
        val content = root.optJSONArray("content") ?: JSONArray()

        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> toolCalls.add(
                    ToolCall(
                        id = block.optString("id"),
                        name = block.optString("name"),
                        argumentsJson = block.optJSONObject("input")?.toString() ?: "{}",
                    ),
                )
            }
        }
        return ProviderReply(
            text = text.toString().trim(),
            toolCalls = toolCalls,
            stopReason = root.optString("stop_reason").ifBlank { null },
        )
    }

    private fun extractErrorMessage(payload: String): String? = runCatching {
        JSONObject(payload).optJSONObject("error")?.optString("message")
    }.getOrNull()?.ifBlank { null }

    companion object {
        private const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

package org.agentnativeos.app.model

import android.util.Log
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.model.ActionJson
import org.agentnativeos.core.model.AnthropicHeaders
import org.agentnativeos.core.model.AuthMode
import org.agentnativeos.core.model.ModelId
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.PlanningContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real planner backed by the Anthropic Messages API. One interface, two auth
 * modes: a Claude subscription OAuth bearer token, or a user-supplied API key.
 *
 * Runs the blocking network call on whatever thread calls it (AgentController
 * uses a background executor). Failures are non-fatal at the loop level: a
 * timeout / bad key returns Abort, which the loop turns into a transparent
 * handoff rather than a crash. Not exercised in CI (needs a credential); the
 * JSON -> action mapping is unit-tested in :core via ActionJson.
 */
class ClaudeModelProvider(
    private val auth: AuthMode,
    private val model: ModelId = ModelId("claude-default"),
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
) : ModelProvider {

    override fun nextAction(context: PlanningContext): AgentAction =
        try {
            ActionJson.parse(callModel(context)) ?: AgentAction.Abort("couldn't parse model output")
        } catch (t: Throwable) {
            Log.w(TAG, "model call failed", t)
            AgentAction.Abort("model unreachable: ${t.message}")
        }

    private fun callModel(context: PlanningContext): String {
        val payload = JSONObject().apply {
            put("model", model.name)
            put("max_tokens", 256)
            put("system", Prompt.system())
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", Prompt.user(context)),
                ),
            )
        }.toString()

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            // Headers (incl. the auth-mode-specific credential + OAuth beta opt-in)
            // come from the pure, unit-tested AnthropicHeaders contract.
            AnthropicHeaders.forAuth(auth).forEach { (name, value) -> setRequestProperty(name, value) }
        }

        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream.bufferedReader().use(BufferedReader::readText)
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${raw.take(200)}")

        // Anthropic returns {"content":[{"type":"text","text":"..."}], ...}
        val content = JSONObject(raw).optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val part = content.getJSONObject(i)
            if (part.optString("type") == "text") sb.append(part.optString("text"))
        }
        return sb.toString()
    }

    companion object {
        const val TAG = "AGENT_MODEL"
    }
}

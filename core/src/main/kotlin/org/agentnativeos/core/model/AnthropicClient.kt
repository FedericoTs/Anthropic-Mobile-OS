package org.agentnativeos.core.model

import java.net.HttpURLConnection
import java.net.URL

/** A minimal HTTP response: status code + raw body. */
data class HttpResponse(val code: Int, val body: String)

/**
 * The HTTP seam. Real calls go through [UrlHttpTransport]; tests inject a fake so
 * the client's request-building and response-parsing are unit-tested offline.
 */
fun interface HttpTransport {
    fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
}

/** Default transport: a blocking HttpURLConnection POST (pure JVM, no Android). */
object UrlHttpTransport : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpResponse(code, text)
    }
}

/**
 * Pure-JVM client for the Anthropic Messages API. Builds the request body with
 * [Json], sends it via a [HttpTransport], and returns the assistant's
 * concatenated text. Auth-mode headers (incl. the OAuth beta opt-in) come from
 * [AnthropicHeaders]. Throws on a non-2xx response so the caller can fail safe.
 */
class AnthropicClient(
    private val auth: AuthMode,
    private val model: ModelId = ModelId.DEFAULT,
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
    private val maxTokens: Int = 256,
    private val transport: HttpTransport = UrlHttpTransport,
) {
    /** One system + user turn. Returns the assistant's text; throws on HTTP error. */
    fun complete(system: String, user: String): String {
        val body = Json.encode(
            linkedMapOf(
                "model" to model.name,
                "max_tokens" to maxTokens,
                "system" to system,
                "messages" to listOf(linkedMapOf("role" to "user", "content" to user)),
            ),
        )
        val response = transport.post(endpoint, AnthropicHeaders.forAuth(auth), body)
        if (response.code !in 200..299) {
            throw RuntimeException("HTTP ${response.code}: ${response.body.take(200)}")
        }
        return extractText(response.body)
    }

    /** Concatenate the text of every {"type":"text", ...} block in `content`. */
    private fun extractText(envelope: String): String {
        val root = Json.parse(envelope) as? Map<*, *> ?: return ""
        val content = root["content"] as? List<*> ?: return ""
        return buildString {
            for (block in content) {
                val b = block as? Map<*, *> ?: continue
                if (b["type"] == "text") append(b["text"] as? String ?: "")
            }
        }
    }
}

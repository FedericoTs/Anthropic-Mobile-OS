package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.ScrollDirection

/**
 * The wire contract between the model and the typed action schema. The planner is
 * prompted to emit exactly one flat JSON object naming the next action; this maps
 * it to an [AgentAction]. Anything unparseable returns null so the provider can
 * fail safe (abort) rather than guess.
 *
 *   {"action":"tap","target":"Battery"}
 *   {"action":"type","target":"Search","text":"ringtone"}
 *   {"action":"launch","package":"com.android.settings"}
 *   {"action":"back"} | {"action":"home"}
 *   {"action":"done","summary":"opened battery"}
 *   {"action":"abort","reason":"can't find it"}
 */
object ActionJson {

    val SCHEMA_HINT: String = """
        Reply with exactly one JSON object and nothing else. Allowed forms:
        {"action":"tap","target":"<visible text>"}
        {"action":"type","target":"<field text>","text":"<value>"}
        {"action":"scroll","target":"<element>","direction":"up|down"}
        {"action":"invoke","capability":"<name>","args":{"<key>":"<value>"}}
        {"action":"launch","package":"<package name>"}
        {"action":"back"}
        {"action":"home"}
        {"action":"done","summary":"<what you accomplished>"}
        {"action":"abort","reason":"<why you can't continue>"}
    """.trimIndent()

    fun parse(raw: String): AgentAction? {
        // The flat parser handles every action whose fields are strings. An "invoke"
        // (nested args) or a "done" carrying structured places falls through to the
        // full Json parser.
        val m = JsonLite.parseFlatObject(raw) ?: return parseInvoke(raw) ?: parseRichDone(raw)
        return when (m["action"]?.lowercase()?.trim()) {
            "tap" -> m["target"]?.let { AgentAction.Tap(it) }
            "type" -> {
                val target = m["target"]
                val value = m["text"]
                if (target != null && value != null) AgentAction.TypeText(target, value) else null
            }
            "scroll" -> {
                val target = m["target"]
                val dir = when (m["direction"]?.lowercase()?.trim()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    else -> null
                }
                if (target != null && dir != null) AgentAction.Scroll(target, dir) else null
            }
            "launch" -> m["package"]?.let { AgentAction.LaunchApp(it) }
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "done" -> AgentAction.Done(m["summary"] ?: "")
            "abort" -> AgentAction.Abort(m["reason"] ?: "")
            "invoke" -> parseInvoke(raw) // capability with nested args
            else -> null
        }
    }

    /** Parse a done that carries structured places (the flat parser can't hold arrays). */
    private fun parseRichDone(raw: String): AgentAction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = Json.parse(raw.substring(start, end + 1)) as? Map<*, *> ?: return null
        if ((obj["action"] as? String)?.lowercase()?.trim() != "done") return null
        val places = (obj["places"] as? List<*>)
            ?.mapNotNull { entry ->
                val p = entry as? Map<*, *> ?: return@mapNotNull null
                val name = (p["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                org.agentnativeos.core.action.Place(
                    name = name,
                    detail = (p["detail"] as? String)?.trim().orEmpty(),
                    query = (p["query"] as? String)?.trim()?.ifEmpty { name } ?: name,
                )
            }
            ?.take(3)
            .orEmpty()
        return AgentAction.Done((obj["summary"] as? String).orEmpty(), places)
    }

    /** Parse a decomposition {"goals":["...","..."]} — null if unreadable/empty; capped at 3. */
    fun parseGoals(raw: String): List<String>? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = Json.parse(raw.substring(start, end + 1)) as? Map<*, *> ?: return null
        val goals = (obj["goals"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf { g -> g.isNotEmpty() } }
            ?.take(3)
        return goals?.takeIf { it.isNotEmpty() }
    }

    /** Parse a verifier verdict {"verified": true|false, "reason": "..."} or null if unreadable. */
    fun parseVerdict(raw: String): VerifyResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = Json.parse(raw.substring(start, end + 1)) as? Map<*, *> ?: return null
        val verified = when (val v = obj["verified"]) {
            is Boolean -> v
            is String -> v.trim().equals("true", ignoreCase = true)
            else -> return null // no clear verdict -> let the caller fail open
        }
        val reason = (obj["reason"] as? String)?.trim() ?: ""
        return VerifyResult(verified, reason)
    }

    /** Parse {"action":"invoke","capability":"x","args":{...}} via the full Json reader. */
    private fun parseInvoke(raw: String): AgentAction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = Json.parse(raw.substring(start, end + 1)) as? Map<*, *> ?: return null
        if ((obj["action"] as? String)?.lowercase()?.trim() != "invoke") return null
        val capability = (obj["capability"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val args = LinkedHashMap<String, String>()
        (obj["args"] as? Map<*, *>)?.forEach { (k, v) ->
            if (k != null && v != null) args[k.toString()] = scalar(v)
        }
        return AgentAction.Invoke(capability, args)
    }

    /** Render a JSON scalar as a clean string (300.0 -> "300"). */
    private fun scalar(v: Any?): String = when (v) {
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }
}

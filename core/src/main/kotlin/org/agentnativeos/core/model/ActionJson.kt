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
        {"action":"launch","package":"<package name>"}
        {"action":"back"}
        {"action":"home"}
        {"action":"done","summary":"<what you accomplished>"}
        {"action":"abort","reason":"<why you can't continue>"}
    """.trimIndent()

    fun parse(raw: String): AgentAction? {
        val m = JsonLite.parseFlatObject(raw) ?: return null
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
            else -> null
        }
    }
}

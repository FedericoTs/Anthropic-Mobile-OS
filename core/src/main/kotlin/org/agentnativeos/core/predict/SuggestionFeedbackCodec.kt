package org.agentnativeos.core.predict

import org.agentnativeos.core.model.Json

/**
 * Persists [SuggestionFeedback] as JSON via the tiny core [Json] reader/writer, so the
 * app can remember what was shown / tapped / dismissed across restarts. Numbers come back
 * from the parser as doubles; they're small (counts + ms timestamps well under 2^53), so
 * the narrowing is exact.
 */
object SuggestionFeedbackCodec {

    fun encode(feedback: SuggestionFeedback): String {
        val perIntent = LinkedHashMap<String, Any?>()
        feedback.perIntent.forEach { (key, f) ->
            perIntent[key] = linkedMapOf(
                "dismissedAtMs" to f.dismissedAtMs,
                "shown" to f.shown,
                "tapped" to f.tapped,
            )
        }
        return Json.encode(
            linkedMapOf(
                "totalShown" to feedback.totalShown,
                "totalTapped" to feedback.totalTapped,
                "perIntent" to perIntent,
            ),
        )
    }

    fun decode(text: String): SuggestionFeedback {
        val root = Json.parse(text) as? Map<*, *> ?: return SuggestionFeedback()
        val perIntent = LinkedHashMap<String, IntentFeedback>()
        (root["perIntent"] as? Map<*, *>)?.forEach { (k, v) ->
            val m = v as? Map<*, *> ?: return@forEach
            val dismissed = (m["dismissedAtMs"] as? List<*>)?.mapNotNull { (it as? Double)?.toLong() } ?: emptyList()
            perIntent[k.toString()] = IntentFeedback(
                dismissedAtMs = dismissed,
                shown = (m["shown"] as? Double)?.toInt() ?: 0,
                tapped = (m["tapped"] as? Double)?.toInt() ?: 0,
            )
        }
        return SuggestionFeedback(
            perIntent = perIntent,
            totalShown = (root["totalShown"] as? Double)?.toInt() ?: 0,
            totalTapped = (root["totalTapped"] as? Double)?.toInt() ?: 0,
        )
    }
}

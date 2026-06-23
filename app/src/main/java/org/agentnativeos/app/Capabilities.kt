package org.agentnativeos.app

import org.agentnativeos.core.model.Capability
import java.net.URLEncoder

/**
 * A pure, JVM-testable mapping from a named capability + string args to the Android
 * Intent that fulfils it. The device actuator turns an [IntentPlan] into a real
 * Intent; keeping the mapping pure lets us unit-test the contract without Android.
 *
 * Every capability here is non-destructive: timers/alarms are reversible, and the
 * messaging ones only OPEN a pre-filled composer (the user still taps send). Auto-
 * committing capabilities (place a call, send now) are intentionally absent — they
 * would be gated as high-side-effect.
 */
data class IntentPlan(
    val action: String,
    val data: String? = null,
    val extras: Map<String, Any> = emptyMap(),
)

object Capabilities {

    // Android intent + extra constants (kept as literals so the planner stays pure).
    private const val ACTION_SET_TIMER = "android.intent.action.SET_TIMER"
    private const val ACTION_SET_ALARM = "android.intent.action.SET_ALARM"
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH"
    private const val ACTION_DIAL = "android.intent.action.DIAL"
    private const val ACTION_SENDTO = "android.intent.action.SENDTO"
    private const val EXTRA_TIMER_LENGTH = "android.intent.extra.alarm.LENGTH"
    private const val EXTRA_ALARM_HOUR = "android.intent.extra.alarm.HOUR"
    private const val EXTRA_ALARM_MINUTES = "android.intent.extra.alarm.MINUTES"
    private const val EXTRA_ALARM_MESSAGE = "android.intent.extra.alarm.MESSAGE"
    private const val EXTRA_ALARM_SKIP_UI = "android.intent.extra.alarm.SKIP_UI"
    private const val EXTRA_QUERY = "query"
    private const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
    private const val EXTRA_TEXT = "android.intent.extra.TEXT"
    private const val EXTRA_SMS_BODY = "sms_body"

    /** The allow-list advertised to the planner each step. */
    val CATALOG: List<Capability> = listOf(
        Capability("set_timer", "Start a countdown timer", listOf("seconds", "message?")),
        Capability("set_alarm", "Set an alarm clock", listOf("hour", "minutes", "message?")),
        Capability("open_url", "Open a web page", listOf("url")),
        Capability("web_search", "Search the web", listOf("query")),
        Capability("dial", "Open the dialer pre-filled (does not call)", listOf("number")),
        Capability("send_sms", "Open a pre-filled SMS (does not send)", listOf("number", "body?")),
        Capability("send_email", "Open a pre-filled email (does not send)", listOf("to", "subject?", "body?")),
        // App-specific deep links — advertised only when the app is installed (see REQUIRED_PACKAGE).
        Capability("maps", "Show a place or directions on the map", listOf("query")),
        Capability("youtube_search", "Search on YouTube", listOf("query")),
        Capability("spotify_search", "Search on Spotify", listOf("query")),
        Capability("whatsapp_message", "Open a WhatsApp chat pre-filled (does not send)", listOf("number", "text?")),
    )

    /** Resolve a capability + args into an [IntentPlan], or null if unknown/invalid. */
    fun plan(capability: String, args: Map<String, String>): IntentPlan? {
        fun req(key: String) = args[key]?.trim()?.takeIf { it.isNotEmpty() }
        fun reqInt(key: String) = req(key)?.toIntOrNull()
        return when (capability.lowercase().trim()) {
            "set_timer" -> reqInt("seconds")?.let { secs ->
                IntentPlan(
                    ACTION_SET_TIMER,
                    extras = buildMap {
                        put(EXTRA_TIMER_LENGTH, secs)
                        put(EXTRA_ALARM_SKIP_UI, true)
                        req("message")?.let { put(EXTRA_ALARM_MESSAGE, it) }
                    },
                )
            }
            "set_alarm" -> {
                val hour = reqInt("hour")
                val minutes = reqInt("minutes") ?: 0
                hour?.let {
                    IntentPlan(
                        ACTION_SET_ALARM,
                        extras = buildMap {
                            put(EXTRA_ALARM_HOUR, hour)
                            put(EXTRA_ALARM_MINUTES, minutes)
                            req("message")?.let { put(EXTRA_ALARM_MESSAGE, it) }
                        },
                    )
                }
            }
            "open_url" -> req("url")?.let { IntentPlan(ACTION_VIEW, data = normalizeUrl(it)) }
            "web_search" -> req("query")?.let { IntentPlan(ACTION_WEB_SEARCH, extras = mapOf(EXTRA_QUERY to it)) }
            "dial" -> req("number")?.let { IntentPlan(ACTION_DIAL, data = "tel:$it") }
            "send_sms" -> req("number")?.let { num ->
                IntentPlan(
                    ACTION_SENDTO,
                    data = "smsto:$num",
                    extras = req("body")?.let { mapOf(EXTRA_SMS_BODY to it) } ?: emptyMap(),
                )
            }
            "send_email" -> req("to")?.let { to ->
                IntentPlan(
                    ACTION_SENDTO,
                    data = "mailto:$to",
                    extras = buildMap {
                        req("subject")?.let { put(EXTRA_SUBJECT, it) }
                        req("body")?.let { put(EXTRA_TEXT, it) }
                    },
                )
            }
            "maps" -> req("query")?.let { IntentPlan(ACTION_VIEW, data = "geo:0,0?q=${enc(it)}") }
            "youtube_search" -> req("query")?.let {
                IntentPlan(ACTION_VIEW, data = "https://www.youtube.com/results?search_query=${enc(it)}")
            }
            "spotify_search" -> req("query")?.let {
                IntentPlan(ACTION_VIEW, data = "https://open.spotify.com/search/${enc(it)}")
            }
            "whatsapp_message" -> req("number")?.let { number ->
                val digits = number.filter { it.isDigit() }
                val text = req("text")?.let { "?text=${enc(it)}" } ?: ""
                IntentPlan(ACTION_VIEW, data = "https://wa.me/$digits$text")
            }
            else -> null
        }
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** App-specific capabilities are only offered when their app is installed. */
    private val REQUIRED_PACKAGE: Map<String, String> = mapOf(
        "youtube_search" to "com.google.android.youtube",
        "spotify_search" to "com.spotify.music",
        "whatsapp_message" to "com.whatsapp",
    )

    /** The package an app-specific capability needs, or null for a generic one. */
    fun requiredPackage(capability: String): String? = REQUIRED_PACKAGE[capability.lowercase().trim()]

    /** Representative args used only to build a probe intent for resolution testing. */
    private val PROBE_ARGS: Map<String, Map<String, String>> = mapOf(
        "set_timer" to mapOf("seconds" to "60"),
        "set_alarm" to mapOf("hour" to "8"),
        "open_url" to mapOf("url" to "https://example.com"),
        "web_search" to mapOf("query" to "x"),
        "dial" to mapOf("number" to "0"),
        "send_sms" to mapOf("number" to "0"),
        "send_email" to mapOf("to" to "a@b.com"),
        "maps" to mapOf("query" to "place"),
        "youtube_search" to mapOf("query" to "x"),
        "spotify_search" to mapOf("query" to "x"),
        "whatsapp_message" to mapOf("number" to "0"),
    )

    /** A representative plan whose action/data is used to test if the device handles a capability. */
    fun probePlan(capability: String): IntentPlan? =
        PROBE_ARGS[capability.lowercase().trim()]?.let { plan(capability, it) }

    /** Narrow the catalog to the capabilities the device resolved as available. */
    fun availableFrom(resolvable: Set<String>): List<Capability> = CATALOG.filter { it.name in resolvable }
}

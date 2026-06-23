package org.agentnativeos.app

import org.agentnativeos.core.model.Capability

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
            else -> null
        }
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
}

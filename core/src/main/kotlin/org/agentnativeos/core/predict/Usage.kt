package org.agentnativeos.core.predict

import org.agentnativeos.core.memory.TaskRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * The predictive layer's signal model — deliberately pure and on-device.
 *
 * A [UsageEvent] is one thing the user did, tagged with WHEN (hour-of-day + weekday vs
 * weekend). The engine looks for what you tend to do in a given time-slice and offers it
 * back as a suggestion you TAP — it never acts on its own. No location, no network, no
 * always-on inference: everything here is arithmetic over your own task history.
 */

enum class DayClass { WEEKDAY, WEEKEND }

/** A coarse slice of the day — general enough to pool samples, fine enough to be useful. */
enum class TimeBucket {
    NIGHT, MORNING, MIDDAY, AFTERNOON, EVENING;

    companion object {
        fun of(hour: Int): TimeBucket = when (hour) {
            in 5..10 -> MORNING
            in 11..13 -> MIDDAY
            in 14..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT // 22–23, 0–4
        }
    }
}

/** One past action, time-tagged. [intent] is the original text (what we'd re-offer). */
data class UsageEvent(
    val intent: String,
    val atMs: Long,
    val hourOfDay: Int,
    val dayClass: DayClass,
) {
    val bucket: TimeBucket get() = TimeBucket.of(hourOfDay)

    companion object {
        /** Derive the time tags from a timestamp in a given zone (device zone in the app). */
        fun at(intent: String, atMs: Long, zone: ZoneId): UsageEvent {
            val z = Instant.ofEpochMilli(atMs).atZone(zone)
            val dayClass = when (z.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> DayClass.WEEKEND
                else -> DayClass.WEEKDAY
            }
            return UsageEvent(intent, atMs, z.hour, dayClass)
        }

        /** Task history is the primary (permission-free) signal — map a record to an event. */
        fun from(record: TaskRecord, zone: ZoneId): UsageEvent = at(record.intent, record.atMs, zone)
    }
}

/** "Now", as the engine sees it: a timestamp plus the current time-slice. */
data class NowContext(val atMs: Long, val hourOfDay: Int, val dayClass: DayClass) {
    val bucket: TimeBucket get() = TimeBucket.of(hourOfDay)

    companion object {
        fun at(atMs: Long, zone: ZoneId): NowContext =
            UsageEvent.at("", atMs, zone).let { NowContext(it.atMs, it.hourOfDay, it.dayClass) }
    }
}

/**
 * Group key for "the same intent". v1 is exact-after-normalization (lowercase, collapse
 * whitespace, drop surrounding punctuation) — so "Set a timer" and "set a  timer." pool,
 * but "25 min" and "20 min" stay distinct. Fuzzy/semantic grouping is a later upgrade.
 */
fun normalizeIntent(intent: String): String =
    intent.lowercase().trim().trim('.', '!', '?', ',').replace(Regex("\\s+"), " ")

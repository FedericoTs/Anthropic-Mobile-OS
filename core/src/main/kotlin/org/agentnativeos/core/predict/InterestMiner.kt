package org.agentnativeos.core.predict

import org.agentnativeos.core.memory.TaskRecord
import kotlin.math.pow

/**
 * Distills the user's recurring INTERESTS from their own completed intents — the taste
 * profile behind personalized answers ("suggest the menu knowing what I like"). Pure
 * arithmetic on-device: tokenize what the user actually asked for, drop function words
 * (EN+IT), weight by recency (30-day half-life), return the strongest recurring terms.
 *
 * The profile is CONTEXT for the planner, never authority: it may flavor an answer or a
 * suggestion; it must never change what the user asked for. (That rule lives in Prompt.)
 */
object InterestMiner {

    fun topInterests(
        records: List<TaskRecord>,
        nowMs: Long,
        limit: Int = 8,
        halfLifeDays: Double = 30.0,
    ): List<String> {
        val scores = LinkedHashMap<String, Double>()
        for (record in records) {
            val weight = 0.5.pow(ageDays(nowMs, record.atMs) / halfLifeDays)
            tokens(record.intent).forEach { token ->
                scores[token] = (scores[token] ?: 0.0) + weight
            }
        }
        return scores.entries
            .filter { it.value >= MIN_SCORE }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun tokens(intent: String): Set<String> =
        intent.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOPWORDS && !it.all(Char::isDigit) }
            .toSet() // per-intent dedupe: one mention per task, however phrased

    private fun ageDays(nowMs: Long, atMs: Long): Double =
        ((nowMs - atMs).coerceAtLeast(0)).toDouble() / (24.0 * 60 * 60 * 1000)

    /** Function words + generic task verbs (EN + IT) that say nothing about taste. */
    private val STOPWORDS = setOf(
        // English
        "the", "and", "then", "for", "with", "that", "this", "his", "her", "its",
        "our", "your", "them", "him", "she", "who", "what", "where", "when", "how",
        "open", "send", "set", "start", "make", "write", "tell", "find", "search",
        "please", "now", "about", "email", "mail", "message", "app", "phone",
        // Italian
        "una", "uno", "gli", "delle", "della", "dei", "del", "per", "con", "che",
        "poi", "dopo", "quindi", "come", "dove", "quando", "cosa", "chi", "mio",
        "mia", "miei", "mie", "apri", "invia", "imposta", "avvia", "manda", "scrivi",
        "trova", "cerca", "dimmi", "adesso", "ora",
    )

    private const val MIN_SCORE = 1.2 // seen more than once recently, or very fresh twice+
}

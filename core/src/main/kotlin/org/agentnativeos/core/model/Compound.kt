package org.agentnativeos.core.model

/**
 * Cheap prefilter for multi-goal intents. Decomposition costs a model call, so we only
 * pay it when the phrasing smells compound (connectives, EN + IT); the decomposer model
 * still decides — it returns ONE goal for a false positive, which costs nothing extra
 * downstream. A miss here just means the single-agent loop handles it (which works).
 */
object Compound {

    private val connectives = listOf(
        " and then ", " and ", " then ", "; ",
        " e poi ", " poi ", " e dopo ", " dopo di che ", " quindi ",
    )

    fun looksCompound(intent: String): Boolean {
        val s = " ${intent.lowercase().trim()} "
        return connectives.any { it in s }
    }
}

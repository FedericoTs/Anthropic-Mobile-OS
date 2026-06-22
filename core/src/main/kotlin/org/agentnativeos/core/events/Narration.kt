package org.agentnativeos.core.events

/** Identifies which task/step/agent an event belongs to (multi-agent ready). */
data class Correlation(val taskId: String, val stepId: Int, val agentId: String = "main")

/**
 * One typed narration stream: the live feed, the audit log, and the eval all
 * consume these events.
 *
 * Redaction is a property of the event TYPE, not a regex over keys: events that
 * could carry untrusted/sensitive screen text expose a redacted [live] view that
 * differs from the full [audit] view. Neither ever contains credentials — those
 * are dropped upstream in perception (password fields are never read).
 */
sealed interface NarrationEvent {
    val correlation: Correlation
    val atMs: Long

    /** Operator-facing line for the live feed (may be coalesced/redacted). */
    fun live(): String

    /** Durable line for the audit log. */
    fun audit(): String

    data class Perceive(
        override val correlation: Correlation,
        override val atMs: Long,
        val nodeCount: Int,
        val pkg: String?,
    ) : NarrationEvent {
        override fun live() = "Looking at ${pkg ?: "the screen"} ($nodeCount elements)"
        override fun audit() = "perceive pkg=$pkg nodes=$nodeCount"
    }

    data class Plan(
        override val correlation: Correlation,
        override val atMs: Long,
        val intent: String,
    ) : NarrationEvent {
        override fun live() = "Thinking about: $intent"
        override fun audit() = "plan intent=${intent.take(200)}"
    }

    data class Propose(
        override val correlation: Correlation,
        override val atMs: Long,
        val action: String,
        val highSideEffect: Boolean,
    ) : NarrationEvent {
        override fun live() =
            if (highSideEffect) "Wants to $action — needs your OK" else "Will $action"
        override fun audit() = "propose action=$action high=$highSideEffect"
    }

    data class Confirm(
        override val correlation: Correlation,
        override val atMs: Long,
        val action: String,
        val approved: Boolean,
    ) : NarrationEvent {
        override fun live() = if (approved) "Approved $action" else "Skipped $action"
        override fun audit() = "confirm action=$action approved=$approved"
    }

    data class Execute(
        override val correlation: Correlation,
        override val atMs: Long,
        val action: String,
        val ok: Boolean,
    ) : NarrationEvent {
        override fun live() = if (ok) "Did $action" else "Couldn't $action"
        override fun audit() = "execute action=$action ok=$ok"
    }

    data class Failure(
        override val correlation: Correlation,
        override val atMs: Long,
        val reason: String,
        val gotAsFar: String,
    ) : NarrationEvent {
        override fun live() = "Stopped: $reason. Got as far as: $gotAsFar"
        override fun audit() = "error reason=$reason gotAsFar=$gotAsFar"
    }

    data class Done(
        override val correlation: Correlation,
        override val atMs: Long,
        val summary: String,
    ) : NarrationEvent {
        override fun live() = "Done: $summary"
        override fun audit() = "done summary=$summary"
    }
}

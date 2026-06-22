package org.agentnativeos.core.model

/**
 * A user-selectable planner model, with short copy for the picker. [shortLabel]
 * is for the compact home pill; [tagline] and [bestFor] help the user choose the
 * right model for the activity.
 */
data class ModelOption(
    val id: ModelId,
    val label: String,
    val shortLabel: String,
    val tagline: String,
    val bestFor: String,
)

/**
 * The curated set of Claude models the user can pick from, ordered cheapest →
 * most capable. The default ([ModelCatalog.DEFAULT]) is the cheapest so everyday
 * use stays inexpensive; the user steps up the ladder for harder, multi-app
 * tasks. Ids/relative cost mirror the Claude model lineup (Haiku < Sonnet < Opus
 * < Fable). We expose one representative per tier rather than every point
 * release, so the choice stays about capability/cost, not version trivia.
 */
object ModelCatalog {

    val OPTIONS: List<ModelOption> = listOf(
        ModelOption(
            id = ModelId("claude-haiku-4-5"),
            label = "Claude Haiku 4.5",
            shortLabel = "Haiku 4.5",
            tagline = "Fastest · cheapest",
            bestFor = "Quick taps, simple lookups, short tasks",
        ),
        ModelOption(
            id = ModelId("claude-sonnet-4-6"),
            label = "Claude Sonnet 4.6",
            shortLabel = "Sonnet 4.6",
            tagline = "Balanced",
            bestFor = "Everyday multi-step intents",
        ),
        ModelOption(
            id = ModelId("claude-opus-4-8"),
            label = "Claude Opus 4.8",
            shortLabel = "Opus 4.8",
            tagline = "Most capable Opus",
            bestFor = "Complex reasoning across many apps",
        ),
        ModelOption(
            id = ModelId("claude-fable-5"),
            label = "Claude Fable 5",
            shortLabel = "Fable 5",
            tagline = "Most capable · priciest",
            bestFor = "The hardest, long-horizon tasks",
        ),
    )

    /** The default option — the cheapest, matching [ModelId.DEFAULT]. */
    val DEFAULT: ModelOption = OPTIONS.first()

    /** Resolve a stored id to its option, falling back to the default if unknown. */
    fun byId(name: String?): ModelOption = OPTIONS.firstOrNull { it.id.name == name } ?: DEFAULT

    fun byId(id: ModelId): ModelOption = byId(id.name)
}

package org.agentnativeos.core.model

/**
 * Holds the active provider and applies swaps at TASK boundaries, never mid-task.
 *
 * - A swap requested mid-task queues; [beginTask] applies it (next-task semantics).
 * - A swap whose factory throws is NON-FATAL: we keep the current provider and
 *   report the failure, so an expired OAuth token or a bad API key can never
 *   hard-fail a live run.
 */
class ProviderRegistry(
    initial: ModelProvider,
    initialModel: ModelId = ModelId.DEFAULT,
) {
    var current: ModelProvider = initial
        private set
    var activeModel: ModelId = initialModel
        private set

    private var pending: (() -> Pair<ModelProvider, ModelId>)? = null

    val hasPendingSwap: Boolean get() = pending != null

    /** Request a swap; it takes effect at the next [beginTask], not immediately. */
    fun requestSwap(factory: () -> Pair<ModelProvider, ModelId>) {
        pending = factory
    }

    /** Call at the start of each task; returns what happened to the swap. */
    fun beginTask(): SwapOutcome {
        val factory = pending ?: return SwapOutcome.NoChange(activeModel)
        pending = null
        return try {
            val (provider, model) = factory()
            current = provider
            activeModel = model
            SwapOutcome.Swapped(model)
        } catch (t: Throwable) {
            SwapOutcome.Failed(activeModel, t.message ?: t.toString())
        }
    }

    sealed interface SwapOutcome {
        data class NoChange(val model: ModelId) : SwapOutcome
        data class Swapped(val model: ModelId) : SwapOutcome
        data class Failed(val keptModel: ModelId, val reason: String) : SwapOutcome
    }
}

package org.agentnativeos.core.events

fun interface EventConsumer {
    fun onEvent(event: NarrationEvent)
}

/** Fan-out of the one typed narration stream. */
interface EventBus {
    fun subscribe(consumer: EventConsumer)
    fun emit(event: NarrationEvent)
}

class InMemoryEventBus : EventBus {
    private val consumers = mutableListOf<EventConsumer>()
    override fun subscribe(consumer: EventConsumer) {
        consumers.add(consumer)
    }
    override fun emit(event: NarrationEvent) {
        // Snapshot so a consumer that subscribes during dispatch isn't called now.
        for (c in consumers.toList()) c.onEvent(event)
    }
}

/** Audit keeps EVERYTHING, durably. */
class AuditLog : EventConsumer {
    private val all = mutableListOf<NarrationEvent>()
    override fun onEvent(event: NarrationEvent) {
        all.add(event)
    }
    fun entries(): List<NarrationEvent> = all.toList()
    fun lines(): List<String> = all.map { it.audit() }
}

/**
 * The live feed applies backpressure (opposite durability to the audit on the
 * same bus): it keeps only the most recent [capacity] events and coalesces
 * consecutive Perceive events for the same step, so the operator isn't flooded
 * with perceive ticks.
 */
class LiveFeed(private val capacity: Int = 50) : EventConsumer {
    private val buffer = ArrayDeque<NarrationEvent>()

    override fun onEvent(event: NarrationEvent) {
        val last = buffer.lastOrNull()
        if (event is NarrationEvent.Perceive &&
            last is NarrationEvent.Perceive &&
            last.correlation == event.correlation
        ) {
            buffer.removeLast() // coalesce repeated perception of the same step
        }
        buffer.addLast(event)
        while (buffer.size > capacity) buffer.removeFirst()
    }

    fun snapshot(): List<NarrationEvent> = buffer.toList()
    fun lines(): List<String> = buffer.map { it.live() }
}

package org.agentnativeos.core.memory

import org.agentnativeos.core.model.Json

/** How a recorded task ended. */
enum class TaskStatus { COMPLETED, ABORTED }

/** One durable entry in the agent's task history. */
data class TaskRecord(
    val intent: String,
    val status: TaskStatus,
    val summary: String,
    val steps: Int,
    val atMs: Long,
)

/**
 * The agent's memory of what it has done. Recent tasks are fed back into planning
 * (so the agent has continuity and can answer "what have you done?") and surfaced
 * on the home screen. The on-device persistence lives in the app; this contract
 * and the JSON codec stay pure so they're unit-tested without Android.
 */
interface TaskMemory {
    /** Record a finished task (most-recent-first ordering is the memory's job). */
    fun record(record: TaskRecord)

    /** The most recent tasks, newest first. */
    fun recent(limit: Int = 10): List<TaskRecord>
}

/** A simple in-memory implementation (default for tests and ephemeral use). */
class InMemoryTaskMemory : TaskMemory {
    private val records = ArrayDeque<TaskRecord>()
    override fun record(record: TaskRecord) { records.addFirst(record) }
    override fun recent(limit: Int): List<TaskRecord> = records.take(limit)
}

/** Pure JSON (de)serialization for persisting task history. */
object TaskMemoryCodec {

    fun encode(records: List<TaskRecord>): String = Json.encode(
        records.map {
            linkedMapOf(
                "intent" to it.intent,
                "status" to it.status.name,
                "summary" to it.summary,
                "steps" to it.steps,
                "at" to it.atMs,
            )
        },
    )

    fun decode(json: String): List<TaskRecord> {
        val arr = Json.parse(json) as? List<*> ?: return emptyList()
        return arr.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val intent = m["intent"] as? String ?: return@mapNotNull null
            val status = runCatching { TaskStatus.valueOf(m["status"] as? String ?: "") }.getOrNull()
                ?: return@mapNotNull null
            TaskRecord(
                intent = intent,
                status = status,
                summary = m["summary"] as? String ?: "",
                steps = (m["steps"] as? Double)?.toInt() ?: 0,
                atMs = (m["at"] as? Double)?.toLong() ?: 0L,
            )
        }
    }
}

package org.agentnativeos.app

import android.content.Context
import org.agentnativeos.core.memory.TaskMemory
import org.agentnativeos.core.memory.TaskMemoryCodec
import org.agentnativeos.core.memory.TaskRecord

/**
 * On-device task history (plain prefs; not a secret). Persists what the agent did
 * so it survives restarts, feeds continuity back into planning, and powers the
 * home screen's recent list. Capped so it can't grow without bound. Intents are
 * the user's own words; nothing here is a credential (those live encrypted).
 */
class PersistentTaskMemory(context: Context) : TaskMemory {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun record(record: TaskRecord) {
        val updated = (listOf(record) + recent(MAX)).take(MAX)
        prefs.edit().putString(KEY, TaskMemoryCodec.encode(updated)).apply()
    }

    override fun recent(limit: Int): List<TaskRecord> =
        TaskMemoryCodec.decode(prefs.getString(KEY, "[]") ?: "[]").take(limit)

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val FILE = "agent_memory"
        const val KEY = "tasks"
        const val MAX = 50
    }
}

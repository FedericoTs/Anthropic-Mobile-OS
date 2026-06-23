package org.agentnativeos.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMemoryTest {

    private fun rec(intent: String, status: TaskStatus = TaskStatus.COMPLETED) =
        TaskRecord(intent, status, "$intent done", steps = 3, atMs = 1L)

    @Test
    fun recordsNewestFirstAndHonorsLimit() {
        val memory = InMemoryTaskMemory()
        memory.record(rec("first"))
        memory.record(rec("second"))
        memory.record(rec("third"))

        assertEquals(listOf("third", "second"), memory.recent(2).map { it.intent })
        assertEquals(3, memory.recent(10).size)
    }

    @Test
    fun codecRoundTripsAllFields() {
        val records = listOf(
            TaskRecord("text mom", TaskStatus.COMPLETED, "sent", 4, 1700000000000L),
            TaskRecord("pay rent", TaskStatus.ABORTED, "you skipped it", 2, 1700000000001L),
        )
        val decoded = TaskMemoryCodec.decode(TaskMemoryCodec.encode(records))
        assertEquals(records, decoded)
    }

    @Test
    fun codecToleratesGarbageAndEmpty() {
        assertTrue(TaskMemoryCodec.decode("not json").isEmpty())
        assertTrue(TaskMemoryCodec.decode("[]").isEmpty())
        // An entry missing required fields is skipped, not fatal.
        assertTrue(TaskMemoryCodec.decode("""[{"summary":"x"}]""").isEmpty())
    }
}

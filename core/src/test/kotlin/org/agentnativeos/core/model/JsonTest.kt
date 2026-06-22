package org.agentnativeos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun parsesNestedObjectsAndArrays() {
        val root = Json.parse("""{"content":[{"type":"text","text":"hi"}],"ok":true}""") as Map<*, *>
        val content = root["content"] as List<*>
        val block = content[0] as Map<*, *>
        assertEquals("text", block["type"])
        assertEquals("hi", block["text"])
        assertEquals(true, root["ok"])
    }

    @Test
    fun decodesStringEscapesIncludingUnicode() {
        val root = Json.parse("""{"text":"line1\nline2 \"q\" ✓"}""") as Map<*, *>
        assertEquals("line1\nline2 \"q\" ✓", root["text"])
    }

    @Test
    fun encodeEscapesAndNests() {
        val out = Json.encode(
            linkedMapOf(
                "model" to "claude-haiku-4-5",
                "max_tokens" to 256,
                "messages" to listOf(linkedMapOf("role" to "user", "content" to "say \"hi\"\n")),
            ),
        )
        assertEquals(
            """{"model":"claude-haiku-4-5","max_tokens":256,""" +
                """"messages":[{"role":"user","content":"say \"hi\"\n"}]}""",
            out,
        )
    }

    @Test
    fun encodeThenParseRoundTrips() {
        val original = linkedMapOf<String, Any?>("a" to "x/y", "b" to listOf("c", "d"))
        val back = Json.parse(Json.encode(original)) as Map<*, *>
        assertEquals("x/y", back["a"])
        assertEquals(listOf("c", "d"), back["b"])
    }

    @Test
    fun malformedInputIsToleratedNotThrown() {
        assertNull(Json.parse(""))
        // A truncated object still parses what it can rather than blowing up.
        assertTrue(Json.parse("""{"a":"b""") is Map<*, *>)
    }
}

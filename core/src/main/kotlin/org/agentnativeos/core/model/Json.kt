package org.agentnativeos.core.model

/**
 * A tiny, dependency-free JSON reader/writer — just enough to build a Messages
 * API request body and read its (nested) response envelope without org.json or
 * Android on the classpath. That keeps the live model client in pure-JVM :core,
 * where the round-trip is unit-testable rather than only reachable on a device.
 *
 * [parse] returns Map<String, Any?>, List<Any?>, String, Double, Boolean, or
 * null. [encode] accepts those same shapes (Int/Long are emitted without a
 * decimal point). This is not a conformance-grade parser; it handles the
 * well-formed JSON the Anthropic API exchanges, including string escapes.
 */
object Json {

    fun parse(input: String): Any? = Parser(input).parseValue()

    fun encode(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(value, sb)
            is Boolean -> sb.append(value.toString())
            is Int, is Long -> sb.append(value.toString())
            is Double -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k.toString(), sb)
                    sb.append(':')
                    write(v, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(v, sb)
                }
                sb.append(']')
            }
            else -> writeString(value.toString(), sb)
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBool()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // consume '{'
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (i < s.length) {
                skipWs()
                if (i >= s.length || s[i] != '"') break
                val key = parseString()
                skipWs()
                if (i < s.length && s[i] == ':') i++
                map[key] = parseValue()
                skipWs()
                when {
                    i < s.length && s[i] == ',' -> { i++; continue }
                    i < s.length && s[i] == '}' -> { i++; break }
                    else -> break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // consume '['
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (i < s.length) {
                list.add(parseValue())
                skipWs()
                when {
                    i < s.length && s[i] == ',' -> { i++; continue }
                    i < s.length && s[i] == ']' -> { i++; break }
                    else -> break
                }
            }
            return list
        }

        private fun parseString(): String {
            val sb = StringBuilder()
            if (i < s.length && s[i] == '"') i++
            while (i < s.length) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) break
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> if (i + 4 <= s.length) {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseBool(): Boolean =
            if (s.startsWith("true", i)) { i += 4; true } else { i += 5; false }

        private fun parseNull(): Any? { i += 4; return null }

        private fun parseNumber(): Any? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull()
        }
    }
}

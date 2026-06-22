package org.agentnativeos.core.model

/**
 * A tiny, dependency-free parser for the flat JSON objects the planner emits.
 * Values are strings only (our action schema needs nothing else), which keeps
 * this pure-Kotlin and unit-testable with no org.json / Android on the classpath.
 * Tolerant of surrounding prose: it extracts the first {...} object.
 */
object JsonLite {

    fun parseFlatObject(input: String): Map<String, String>? {
        val start = input.indexOf('{')
        val end = input.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = input.substring(start + 1, end)
        val map = LinkedHashMap<String, String>()
        var i = 0

        fun skipWs() { while (i < body.length && body[i].isWhitespace()) i++ }

        fun parseString(): String? {
            skipWs()
            if (i >= body.length || body[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < body.length) {
                when (val c = body[i++]) {
                    '\\' -> {
                        if (i >= body.length) return null
                        when (val e = body[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            else -> sb.append(e)
                        }
                    }
                    '"' -> return sb.toString()
                    else -> sb.append(c)
                }
            }
            return null
        }

        while (true) {
            skipWs()
            if (i >= body.length) break
            val key = parseString() ?: return null
            skipWs()
            if (i >= body.length || body[i] != ':') return null
            i++
            val value = parseString() ?: return null
            map[key] = value
            skipWs()
            if (i < body.length && body[i] == ',') { i++; continue }
            break
        }
        return map
    }
}

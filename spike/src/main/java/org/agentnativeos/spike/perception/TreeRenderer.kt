package org.agentnativeos.spike.perception

/**
 * Serializes a [UiNode] tree into the indented text the spike records as
 * evidence. Pure: no Android, no logging, no I/O — so the exact perceive output
 * and the credential-redaction rule are unit-tested deterministically.
 *
 * Security stance, baked in (see DESIGN.md / Security Model): the text of a
 * password field is never rendered. The spike records *that* a field exists,
 * never its secret contents.
 */
object TreeRenderer {

    data class Result(val text: String, val nodeCount: Int)

    fun render(root: UiNode): Result {
        val sb = StringBuilder()
        var count = 0

        fun walk(node: UiNode, depth: Int) {
            count++
            val text = when {
                node.isPassword -> "[REDACTED:password]"
                node.text != null -> "\"${sanitize(node.text!!)}\""
                else -> "null"
            }
            val desc = node.contentDescription?.let { "\"${sanitize(it)}\"" } ?: "null"
            sb.append("  ".repeat(depth))
                .append('<').append(shortClass(node.className)).append('>')
                .append(" pkg=").append(node.packageName ?: "-")
                .append(" id=").append(node.viewId ?: "-")
                .append(" text=").append(text)
                .append(" desc=").append(desc)
                .append(" bounds=").append(node.bounds)
                .append(" flags=[")
                .append(if (node.isClickable) 'C' else '-')
                .append(if (node.isFocusable) 'F' else '-')
                .append(if (node.isEditable) 'E' else '-')
                .append(if (node.isScrollable) 'S' else '-')
                .append(if (node.isCheckable) 'K' else '-')
                .append(if (node.isPassword) 'P' else '-')
                .append("]\n")
            node.children.forEach { walk(it, depth + 1) }
        }

        walk(root, 0)
        val header = "ROOT package=${root.packageName ?: "-"} nodeCount=$count\n"
        return Result(header + sb, count)
    }

    private fun sanitize(s: String): String = s.replace("\n", "\\n").replace("\"", "'")

    private fun shortClass(c: String?): String =
        c?.substringAfterLast('.')?.ifEmpty { "View" } ?: "View"
}

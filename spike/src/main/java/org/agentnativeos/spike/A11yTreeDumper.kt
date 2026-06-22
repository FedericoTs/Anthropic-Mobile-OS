package org.agentnativeos.spike

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializes an [AccessibilityNodeInfo] tree into a human-readable, indented form
 * and persists it. This is the "perceive" half of the spike: what the agent can
 * actually see of another app.
 *
 * Security stance, baked in from day one (see DESIGN.md / Security Model): the
 * text of password fields is never logged or written. The spike measures *whether*
 * a field is perceivable, not its secret contents.
 */
object A11yTreeDumper {

    private const val TAG = "SPIKE_TREE"

    /** Render the tree rooted at [root] to a string and echo it to logcat. */
    fun dump(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        var count = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            count++
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val text = when {
                node.isPassword -> "[REDACTED:password]"
                node.text != null -> "\"${sanitize(node.text.toString())}\""
                else -> "null"
            }
            val desc = node.contentDescription?.let { "\"${sanitize(it.toString())}\"" } ?: "null"
            sb.append("  ".repeat(depth))
                .append('<').append(shortClass(node.className)).append('>')
                .append(" pkg=").append(node.packageName ?: "-")
                .append(" id=").append(node.viewIdResourceName ?: "-")
                .append(" text=").append(text)
                .append(" desc=").append(desc)
                .append(" bounds=").append(bounds.toShortString())
                .append(" flags=[")
                .append(if (node.isClickable) 'C' else '-')
                .append(if (node.isFocusable) 'F' else '-')
                .append(if (node.isEditable) 'E' else '-')
                .append(if (node.isScrollable) 'S' else '-')
                .append(if (node.isCheckable) 'K' else '-')
                .append(if (node.isPassword) 'P' else '-')
                .append("]\n")
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
            }
        }

        walk(root, 0)
        val out = "ROOT package=${root.packageName} nodeCount=$count\n" + sb
        Log.i(TAG, "\n$out")
        return out
    }

    /** Write [content] to the app's external files dir. Returns the absolute path. */
    fun persist(context: Context, content: String, rootPackage: String): String {
        return try {
            val dir = File(context.getExternalFilesDir(null), "dumps").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val safePkg = rootPackage.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "tree-$ts-$safePkg.txt")
            file.writeText(content)
            Log.i(TAG, "dump written: ${file.absolutePath}")
            file.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "failed to persist dump", t)
            "(persist failed: ${t.message})"
        }
    }

    private fun sanitize(s: String): String = s.replace("\n", "\\n").replace("\"", "'")

    private fun shortClass(c: CharSequence?): String =
        c?.toString()?.substringAfterLast('.')?.ifEmpty { "View" } ?: "View"
}

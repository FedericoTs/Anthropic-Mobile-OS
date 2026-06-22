package org.agentnativeos.spike

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.agentnativeos.spike.perception.AccessibilityUiNode
import org.agentnativeos.spike.perception.TreeRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "perceive" half of the spike: render the active window's a11y tree and
 * persist it as evidence. The rendering + credential redaction live in the pure,
 * unit-tested [TreeRenderer]; this object is just the Android glue (logcat + file).
 */
object A11yTreeDumper {

    private const val TAG = "SPIKE_TREE"

    /** Render the tree rooted at [root] and echo it to logcat. Returns the text. */
    fun dump(root: AccessibilityNodeInfo): String {
        val result = TreeRenderer.render(AccessibilityUiNode(root))
        Log.i(TAG, "\n${result.text}")
        return result.text
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
}

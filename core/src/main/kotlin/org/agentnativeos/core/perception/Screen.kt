package org.agentnativeos.core.perception

/** Screen-space rectangle (Android-free mirror of android.graphics.Rect). */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    override fun toString(): String = "[$left,$top][$right,$bottom]"
}

/**
 * A perceived accessibility node. Deliberately Android-free so the agent brain
 * (loop, planner, policy) is testable on the JVM. The Android app provides an
 * adapter from AccessibilityNodeInfo to this interface — the same shape the T0
 * spike proved it can read.
 */
interface ScreenNode {
    val packageName: String?
    val className: String?
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val bounds: Bounds
    val isClickable: Boolean
    val isEditable: Boolean
    val isPassword: Boolean
    val children: List<ScreenNode>
}

/** A single perception of the foreground window. */
data class Observation(
    val rootPackage: String?,
    val root: ScreenNode?,
    val capturedAtMs: Long,
) {
    val isEmpty: Boolean get() = root == null
}

/** Locates a node from a human query, shared by the loop and the actuator. */
object NodeFinder {

    fun matches(node: ScreenNode, query: String): Boolean =
        sequenceOf(node.text, node.contentDescription, node.viewId)
            .any { it != null && it.contains(query, ignoreCase = true) }

    /** Breadth-first search for the first node matching [query]. */
    fun find(root: ScreenNode?, query: String): ScreenNode? {
        root ?: return null
        val queue = ArrayDeque<ScreenNode>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (matches(node, query)) return node
            queue.addAll(node.children)
        }
        return null
    }

    fun nodeCount(root: ScreenNode?): Int {
        root ?: return 0
        var count = 0
        val queue = ArrayDeque<ScreenNode>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            count++
            queue.addAll(node.children)
        }
        return count
    }

    /** Visible (non-password) text of the whole tree, newline-joined. */
    fun visibleText(root: ScreenNode?): String {
        root ?: return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<ScreenNode>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isPassword) node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
            queue.addAll(node.children)
        }
        return sb.toString()
    }
}

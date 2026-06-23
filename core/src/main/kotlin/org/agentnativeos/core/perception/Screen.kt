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

    /**
     * A compact, structured description of the screen for the planner: one line per
     * labelled element, tagged by how it can be used. Crucially it includes
     * contentDescription, not just text — so icon buttons, keypad digits, toggles and
     * play/start controls (which often have no visible text) are visible to the model.
     *
     *   [tap] 5            (clickable)
     *   [input] Search     (editable field; shows its current text/hint)
     *   [text] 00:05.00    (static label)
     *
     * Password nodes are redacted at the source. Duplicate lines are collapsed and the
     * list is capped so a busy launcher doesn't blow up the prompt.
     */
    fun describe(root: ScreenNode?, maxLines: Int = 150): String {
        root ?: return ""
        val lines = LinkedHashSet<String>()
        val queue = ArrayDeque<ScreenNode>()
        queue.add(root)
        while (queue.isNotEmpty() && lines.size < maxLines) {
            val node = queue.removeFirst()
            if (!node.isPassword) {
                val label = (node.text?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.takeIf { it.isNotBlank() })
                    ?.trim()?.replace('\n', ' ')
                if (label != null) {
                    val tag = when {
                        node.isEditable -> "[input]"
                        node.isClickable -> "[tap]"
                        else -> "[text]"
                    }
                    lines.add("$tag $label")
                }
            }
            queue.addAll(node.children)
        }
        return lines.joinToString("\n")
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

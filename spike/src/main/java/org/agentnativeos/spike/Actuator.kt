package org.agentnativeos.spike

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.agentnativeos.spike.perception.NodeMatcher

/**
 * The "act" half of the spike: one tap and one text entry, the two primitives T0
 * needs to prove. Each method logs which mechanism actually worked
 * (ACTION_CLICK vs a dispatched gesture; ACTION_SET_TEXT), which is exactly the
 * data the spike is collecting.
 */
object Actuator {

    private const val TAG = "SPIKE_ACT"

    /**
     * Find a node matching [query] (by text, content-description, or view id),
     * then try ACTION_CLICK on it or its nearest clickable ancestor; if that
     * fails, fall back to a dispatched tap gesture at the node's center.
     */
    fun tapByText(service: SpikeAccessibilityService, query: String): Boolean {
        val root = service.rootInActiveWindow ?: run {
            Log.w(TAG, "tapByText: no active window")
            return false
        }
        val node = findNode(root) { matches(it, query) } ?: run {
            Log.w(TAG, "tapByText: no node matching \"$query\"")
            return false
        }

        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.i(TAG, "tapByText(\"$query\"): ACTION_CLICK ok")
            return true
        }

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val ok = tapAt(service, bounds.exactCenterX(), bounds.exactCenterY())
        Log.i(TAG, "tapByText(\"$query\"): gesture tap @${bounds.exactCenterX()},${bounds.exactCenterY()} -> $ok")
        return ok
    }

    /** Dispatch a tap gesture at absolute screen coordinates. */
    fun tapAt(service: SpikeAccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    /**
     * Set text on an editable field. If [targetQuery] is given, locate the field
     * by it; otherwise use the focused editable node, else the first editable node.
     */
    fun typeText(service: SpikeAccessibilityService, targetQuery: String?, value: String): Boolean {
        val root = service.rootInActiveWindow ?: run {
            Log.w(TAG, "typeText: no active window")
            return false
        }
        val field = when {
            targetQuery != null ->
                findNode(root) { it.isEditable && matches(it, targetQuery) }
                    ?: findNode(root) { matches(it, targetQuery) }
            else ->
                findNode(root) { it.isEditable && it.isFocused }
                    ?: findNode(root) { it.isEditable }
        } ?: run {
            Log.w(TAG, "typeText: no editable field found (target=$targetQuery)")
            return false
        }

        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.i(TAG, "typeText(target=$targetQuery): ACTION_SET_TEXT -> $ok")
        return ok
    }

    private fun matches(node: AccessibilityNodeInfo, query: String): Boolean =
        NodeMatcher.matches(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            query = query,
        )

    /** Breadth-first search for the first node satisfying [predicate]. */
    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}

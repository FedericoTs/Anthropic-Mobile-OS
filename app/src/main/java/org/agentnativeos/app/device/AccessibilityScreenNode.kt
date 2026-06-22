package org.agentnativeos.app.device

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.agentnativeos.core.perception.Bounds
import org.agentnativeos.core.perception.ScreenNode

/** Adapts a live AccessibilityNodeInfo into the core's Android-free ScreenNode. */
class AccessibilityScreenNode(private val node: AccessibilityNodeInfo) : ScreenNode {
    override val packageName: String? get() = node.packageName?.toString()
    override val className: String? get() = node.className?.toString()
    override val viewId: String? get() = node.viewIdResourceName
    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()

    override val bounds: Bounds
        get() {
            val r = Rect()
            node.getBoundsInScreen(r)
            return Bounds(r.left, r.top, r.right, r.bottom)
        }

    override val isClickable: Boolean get() = node.isClickable
    override val isEditable: Boolean get() = node.isEditable
    override val isPassword: Boolean get() = node.isPassword

    override val children: List<ScreenNode>
        get() = (0 until node.childCount).mapNotNull { i ->
            node.getChild(i)?.let { AccessibilityScreenNode(it) }
        }
}

package org.agentnativeos.spike.perception

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Live adapter: wraps a real [AccessibilityNodeInfo] as a [UiNode] so the pure
 * perception logic can run over the on-device tree. [raw] is exposed so the
 * actuator can act on the underlying node after a match.
 */
class AccessibilityUiNode(val raw: AccessibilityNodeInfo) : UiNode {
    override val className: String? get() = raw.className?.toString()
    override val packageName: String? get() = raw.packageName?.toString()
    override val viewId: String? get() = raw.viewIdResourceName
    override val text: String? get() = raw.text?.toString()
    override val contentDescription: String? get() = raw.contentDescription?.toString()

    override val bounds: Bounds
        get() {
            val r = Rect()
            raw.getBoundsInScreen(r)
            return Bounds(r.left, r.top, r.right, r.bottom)
        }

    override val isClickable: Boolean get() = raw.isClickable
    override val isFocusable: Boolean get() = raw.isFocusable
    override val isEditable: Boolean get() = raw.isEditable
    override val isScrollable: Boolean get() = raw.isScrollable
    override val isCheckable: Boolean get() = raw.isCheckable
    override val isPassword: Boolean get() = raw.isPassword

    override val children: List<UiNode>
        get() = (0 until raw.childCount).mapNotNull { i ->
            raw.getChild(i)?.let { AccessibilityUiNode(it) }
        }
}

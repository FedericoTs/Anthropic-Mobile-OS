package org.agentnativeos.spike.perception

/**
 * A minimal, Android-free view of an accessibility node.
 *
 * Perception logic (rendering, redaction, matching) is written against this
 * interface so it can be unit-tested on the JVM with no device or Robolectric.
 * The live adapter is [AccessibilityUiNode]; tests use plain fakes.
 *
 * On a GO verdict this package is what T2's perception layer grows from, so it
 * is deliberately decoupled from the AccessibilityService.
 */
interface UiNode {
    val className: String?
    val packageName: String?
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val bounds: Bounds
    val isClickable: Boolean
    val isFocusable: Boolean
    val isEditable: Boolean
    val isScrollable: Boolean
    val isCheckable: Boolean
    val isPassword: Boolean
    val children: List<UiNode>
}

/** Screen-space rectangle, mirrors android.graphics.Rect without the Android dep. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    override fun toString(): String = "[$left,$top][$right,$bottom]"
}

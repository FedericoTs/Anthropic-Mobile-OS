package org.agentnativeos.spike.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the spike's perception logic. No device, no Robolectric.
 * These guard the two things that must never silently break: credential
 * redaction and how the agent locates a node from a human query.
 */
class PerceptionLogicTest {

    /** Minimal in-memory [UiNode] for tests. */
    private data class FakeNode(
        override val className: String? = "android.widget.TextView",
        override val packageName: String? = "com.example.app",
        override val viewId: String? = null,
        override val text: String? = null,
        override val contentDescription: String? = null,
        override val bounds: Bounds = Bounds(0, 0, 100, 50),
        override val isClickable: Boolean = false,
        override val isFocusable: Boolean = false,
        override val isEditable: Boolean = false,
        override val isScrollable: Boolean = false,
        override val isCheckable: Boolean = false,
        override val isPassword: Boolean = false,
        override val children: List<UiNode> = emptyList(),
    ) : UiNode

    // --- TreeRenderer: redaction ------------------------------------------------

    @Test
    fun render_redactsPasswordFieldText_andNeverLeaksTheSecret() {
        val secret = "hunter2-SUPER-SECRET"
        val tree = FakeNode(
            className = "android.widget.FrameLayout",
            children = listOf(
                FakeNode(
                    className = "android.widget.EditText",
                    text = secret,
                    isPassword = true,
                    isEditable = true,
                ),
            ),
        )

        val result = TreeRenderer.render(tree)

        assertFalse("secret must never appear in a dump", result.text.contains(secret))
        assertTrue("password field text must be redacted", result.text.contains("[REDACTED:password]"))
        assertTrue("password flag must be recorded", result.text.contains("P]"))
    }

    @Test
    fun render_keepsNonPasswordText() {
        val tree = FakeNode(text = "Network & internet")
        val result = TreeRenderer.render(tree)
        assertTrue(result.text.contains("\"Network & internet\""))
    }

    // --- TreeRenderer: structure ------------------------------------------------

    @Test
    fun render_countsEveryNode_andNestsByDepth() {
        val tree = FakeNode(
            className = "android.widget.LinearLayout",
            children = listOf(
                FakeNode(text = "A"),
                FakeNode(
                    className = "android.widget.LinearLayout",
                    children = listOf(FakeNode(text = "B")),
                ),
            ),
        )

        val result = TreeRenderer.render(tree)

        assertEquals(4, result.nodeCount) // root + A + nested layout + B
        assertTrue("header reports node count", result.text.startsWith("ROOT package=com.example.app nodeCount=4"))
        // "B" is two levels deep -> indented four spaces.
        assertTrue(result.text.lines().any { it.startsWith("    <TextView>") && it.contains("\"B\"") })
    }

    @Test
    fun render_emptyTextIsNull_notQuoted() {
        val result = TreeRenderer.render(FakeNode(text = null, contentDescription = null))
        assertTrue(result.text.contains("text=null"))
        assertTrue(result.text.contains("desc=null"))
    }

    // --- NodeMatcher ------------------------------------------------------------

    @Test
    fun matcher_isCaseInsensitiveSubstring_acrossText_desc_id() {
        assertTrue(NodeMatcher.matches(FakeNode(text = "Battery Saver"), "battery"))
        assertTrue(NodeMatcher.matches(FakeNode(contentDescription = "Open settings"), "SETTINGS"))
        assertTrue(NodeMatcher.matches(FakeNode(viewId = "com.x:id/search_box"), "search"))
    }

    @Test
    fun matcher_returnsFalseWhenNothingMatches() {
        assertFalse(NodeMatcher.matches(FakeNode(text = "Wi-Fi"), "bluetooth"))
        assertFalse(NodeMatcher.matches(FakeNode(), "anything"))
    }
}

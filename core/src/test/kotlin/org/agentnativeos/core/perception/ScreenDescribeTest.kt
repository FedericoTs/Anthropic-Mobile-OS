package org.agentnativeos.core.perception

import org.agentnativeos.core.FakeNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDescribeTest {

    private fun tree(vararg kids: ScreenNode) = FakeNode(children = kids.toList())

    @Test
    fun includesContentDescriptionNotJustText() {
        // A keypad digit / icon button with no text, only a contentDescription.
        val root = tree(
            FakeNode(text = "00:00.00"),
            FakeNode(contentDescription = "5", isClickable = true),
            FakeNode(contentDescription = "Avvia", isClickable = true),
        )
        val out = NodeFinder.describe(root)
        assertTrue("[tap] 5" in out)
        assertTrue("[tap] Avvia" in out)
        assertTrue("[text] 00:00.00" in out)
    }

    @Test
    fun tagsEditableAndClickable() {
        val root = tree(
            FakeNode(text = "Search", isEditable = true),
            FakeNode(text = "Go", isClickable = true),
            FakeNode(text = "Heading"),
        )
        val out = NodeFinder.describe(root)
        assertTrue("[input] Search" in out)
        assertTrue("[tap] Go" in out)
        assertTrue("[text] Heading" in out)
    }

    @Test
    fun redactsPasswordNodes() {
        val out = NodeFinder.describe(tree(FakeNode(text = "hunter2", isPassword = true)))
        assertFalse("password content must never be surfaced", out.contains("hunter2"))
    }

    @Test
    fun collapsesDuplicatesAndCaps() {
        val many = (1..500).map { FakeNode(text = "Dup", isClickable = true) }
        val out = NodeFinder.describe(FakeNode(children = many), maxLines = 150)
        // All 500 identical nodes collapse to a single line.
        assertTrue(out.lines().count { it == "[tap] Dup" } == 1)
        assertTrue(out.lines().size <= 150)
    }
}

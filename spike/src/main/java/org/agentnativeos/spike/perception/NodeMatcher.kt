package org.agentnativeos.spike.perception

/**
 * How the spike locates a node from a human query: case-insensitive substring
 * match against visible text, content-description, or view id. Pure and shared
 * by the dumper and the actuator so "find" behaves identically everywhere.
 */
object NodeMatcher {

    fun matches(text: String?, contentDescription: String?, viewId: String?, query: String): Boolean =
        sequenceOf(text, contentDescription, viewId)
            .any { it != null && it.contains(query, ignoreCase = true) }

    fun matches(node: UiNode, query: String): Boolean =
        matches(node.text, node.contentDescription, node.viewId, query)
}

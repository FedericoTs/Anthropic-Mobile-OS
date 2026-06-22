package org.agentnativeos.core

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.loop.ActionOutcome
import org.agentnativeos.core.loop.Actuator
import org.agentnativeos.core.loop.Perceiver
import org.agentnativeos.core.perception.Bounds
import org.agentnativeos.core.perception.Observation
import org.agentnativeos.core.perception.ScreenNode

/** Minimal in-memory [ScreenNode] for tests. */
data class FakeNode(
    override val packageName: String? = "com.test",
    override val className: String? = "View",
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val bounds: Bounds = Bounds(0, 0, 10, 10),
    override val isClickable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isPassword: Boolean = false,
    override val children: List<ScreenNode> = emptyList(),
) : ScreenNode

/** A screen whose children are clickable text nodes with the given labels. */
fun screenWith(vararg labels: String): Observation {
    val kids = labels.map { FakeNode(text = it, isClickable = true) }
    return Observation("com.test", FakeNode(children = kids), 0L)
}

/** Perceiver that always returns the same screen. */
class StaticPerceiver(private val observation: Observation) : Perceiver {
    override fun perceive(): Observation = observation
}

/** Perceiver that returns each observation in turn (last one repeats). */
class SequencePerceiver(private val frames: List<Observation>) : Perceiver {
    private var i = 0
    override fun perceive(): Observation {
        val frame = frames[minOf(i, frames.size - 1)]
        i++
        return frame
    }
}

/** Records every action it is asked to perform; configurable success. */
class RecordingActuator(private val succeed: Boolean = true, private val detail: String = "") : Actuator {
    val performed = mutableListOf<AgentAction>()
    override fun act(action: AgentAction): ActionOutcome {
        performed.add(action)
        return ActionOutcome(succeed, detail)
    }
}

package org.agentnativeos.core.undo

import org.agentnativeos.core.action.AgentAction

data class UndoEntry(val action: AgentAction, val compensation: Compensation)

sealed interface UndoResult {
    /** Perform this inverse action to undo the last step. */
    data class Perform(val inverse: AgentAction, val description: String) : UndoResult

    /** The last step is irreversible — you can't rewind past it. */
    data class Blocked(val reason: String) : UndoResult

    data object NothingToUndo : UndoResult
}

/**
 * Records how to undo each executed step as the loop runs, with an explicit
 * irreversible barrier: once the agent has done something it can't take back (sent
 * money, deleted an account), [undoLast] refuses to rewind past it rather than
 * pretending it can.
 */
class UndoStack {

    private val entries = ArrayDeque<UndoEntry>()

    fun record(action: AgentAction, compensation: Compensation) {
        if (compensation is Compensation.None) return
        entries.addLast(UndoEntry(action, compensation))
    }

    fun depth(): Int = entries.size

    val canUndo: Boolean
        get() = entries.lastOrNull()?.compensation is Compensation.Undoable

    /**
     * The full sequence of inverses to rewind the run, newest step first, stopping at
     * an irreversible barrier (you can't rewind past a committed action). Drains the
     * undoable entries it returns; the barrier and anything before it stay protected.
     */
    fun rewindPlan(): List<UndoResult.Perform> {
        val plan = mutableListOf<UndoResult.Perform>()
        while (true) {
            when (val r = undoLast()) {
                is UndoResult.Perform -> plan += r
                is UndoResult.Blocked -> return plan // can't rewind past this
                UndoResult.NothingToUndo -> return plan
            }
        }
    }

    /** Pop the last undoable step and return its inverse, or explain why not. */
    fun undoLast(): UndoResult {
        val top = entries.lastOrNull() ?: return UndoResult.NothingToUndo
        return when (val c = top.compensation) {
            is Compensation.Undoable -> {
                entries.removeLast()
                UndoResult.Perform(c.inverse, c.description)
            }
            is Compensation.Irreversible -> UndoResult.Blocked(c.reason)
            Compensation.None -> {
                entries.removeLast()
                UndoResult.NothingToUndo
            }
        }
    }
}

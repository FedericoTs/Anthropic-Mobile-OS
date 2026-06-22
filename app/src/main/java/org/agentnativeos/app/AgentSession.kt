package org.agentnativeos.app

import android.os.Handler
import android.os.Looper
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.loop.LoopResult
import org.agentnativeos.core.undo.UndoStack
import java.util.concurrent.SynchronousQueue

/**
 * Process-wide hub between a running agent and the narration UI. Both the real
 * AgentLoop (via [AgentController]) and the canned [NarrationDemo] push events
 * here; [org.agentnativeos.app.ui.NarrationActivity] subscribes and renders them
 * live.
 *
 * It also bridges the loop's out-of-model confirm gate to the UI:
 * [awaitConfirmation] blocks the agent thread until the user taps Approve/Skip,
 * and [requestStop] cancels the run between steps.
 */
object AgentSession {

    interface Listener {
        fun onEvent(event: NarrationEvent)
        fun onConfirmRequested(action: AgentAction, reason: String)
        fun onConfirmResolved()
        fun onFinished(result: LoopResult)
    }

    private val main = Handler(Looper.getMainLooper())
    private val events = mutableListOf<NarrationEvent>()
    private val confirmChannel = SynchronousQueue<Boolean>()

    @Volatile
    private var listener: Listener? = null

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var stopRequested: Boolean = false
        private set

    @Volatile
    var pendingConfirm: Pair<AgentAction, String>? = null
        private set

    /** The undo stack for the current/last run (E2-4) — ready for an Undo affordance. */
    @Volatile
    var lastUndoStack: UndoStack? = null

    fun setListener(l: Listener?) {
        listener = l
    }

    fun snapshot(): List<NarrationEvent> = synchronized(events) { events.toList() }

    fun begin() {
        synchronized(events) { events.clear() }
        stopRequested = false
        running = true
    }

    /** Called from the agent thread (loop or demo). */
    fun emit(event: NarrationEvent) {
        synchronized(events) { events.add(event) }
        main.post { listener?.onEvent(event) }
    }

    /** Loop-thread blocking confirm: returns the user's decision (false if stopped). */
    fun awaitConfirmation(action: AgentAction, reason: String): Boolean {
        if (stopRequested) return false
        pendingConfirm = action to reason
        main.post { listener?.onConfirmRequested(action, reason) }
        val approved = try {
            confirmChannel.take()
        } catch (e: InterruptedException) {
            false
        }
        pendingConfirm = null
        main.post { listener?.onConfirmResolved() }
        return approved && !stopRequested
    }

    /** Called from the UI thread when the user taps Approve/Skip. */
    fun resolveConfirmation(approved: Boolean) {
        confirmChannel.offer(approved)
    }

    /** The Stop control: cancels the run and unblocks any pending confirm. */
    fun requestStop() {
        stopRequested = true
        confirmChannel.offer(false)
    }

    fun finish(result: LoopResult) {
        running = false
        main.post { listener?.onFinished(result) }
    }
}

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

    /** Whether the narration UI is in the foreground (set by NarrationActivity).
     *  When it isn't, a confirm floats over the app the agent is driving instead. */
    @Volatile
    var uiForeground: Boolean = false

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
        main.post {
            listener?.onEvent(event)
            // While the agent acts in another app (our UI backgrounded), stream the live
            // step into the floating overlay so it can be watched, not just approved.
            if (running && !uiForeground && event !is NarrationEvent.StepTiming) {
                val line = event.live()
                if (line.isNotBlank()) OverlayConfirm.showStep(line)
            }
        }
    }

    /** Loop-thread blocking confirm: returns the user's decision (false if stopped). */
    fun awaitConfirmation(action: AgentAction, reason: String): Boolean {
        if (stopRequested) return false
        pendingConfirm = action to reason
        main.post {
            listener?.onConfirmRequested(action, reason)
            // If the agent has navigated away from our UI, surface the confirm as a
            // floating card over that app so it can't be missed in the background.
            if (!uiForeground) OverlayConfirm.show(reason)
        }
        val approved = try {
            confirmChannel.take()
        } catch (e: InterruptedException) {
            false
        }
        pendingConfirm = null
        main.post {
            listener?.onConfirmResolved()
            OverlayConfirm.hide()
        }
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
        OverlayConfirm.hide()
    }

    fun finish(result: LoopResult) {
        running = false
        main.post {
            listener?.onFinished(result)
            OverlayConfirm.hide()
            // Don't strand the user in the app the agent was driving (e.g. Maps left
            // open after a lookup): when the run ends with our UI backgrounded, bring
            // the answer back to the foreground.
            if (!uiForeground) {
                org.agentnativeos.app.device.AgentAccessibilityService.instance?.let { svc ->
                    svc.startActivity(
                        android.content.Intent(svc, org.agentnativeos.app.ui.NarrationActivity::class.java)
                            .addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            ),
                    )
                }
            }
        }
    }
}

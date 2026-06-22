package org.agentnativeos.app

import android.util.Log
import org.agentnativeos.app.device.AgentAccessibilityService
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.PolicyGate
import org.agentnativeos.core.events.AuditLog
import org.agentnativeos.core.events.EventConsumer
import org.agentnativeos.core.events.InMemoryEventBus
import org.agentnativeos.core.loop.AgentLoop
import org.agentnativeos.core.loop.ConfirmationHandler
import org.agentnativeos.core.loop.LoopResult
import org.agentnativeos.core.model.ModelProvider
import org.agentnativeos.core.model.ScriptedModelProvider
import org.agentnativeos.core.undo.UndoStack
import java.util.concurrent.Executors

/**
 * Wires the core AgentLoop to the device (the AccessibilityService is the
 * Perceiver + Actuator) and runs it off the main thread. Narration is streamed to
 * logcat under the AGENT tag and kept in an in-memory audit; a UI feed can
 * subscribe to the same bus.
 */
object AgentController {

    private const val TAG = "AGENT"
    private val executor = Executors.newSingleThreadExecutor()

    fun run(
        intent: String,
        provider: ModelProvider,
        confirm: ConfirmationHandler,
        onResult: (LoopResult) -> Unit = {},
    ) {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "service not connected")
            onResult(LoopResult.Aborted("agent service not enabled", "nothing yet", 0))
            return
        }
        AgentSession.begin()
        val undo = UndoStack()
        AgentSession.lastUndoStack = undo
        executor.execute {
            val bus = InMemoryEventBus()
            val audit = AuditLog()
            bus.subscribe(audit)
            bus.subscribe(EventConsumer { event -> Log.i(TAG, event.audit()) })
            bus.subscribe(EventConsumer { event -> AgentSession.emit(event) })
            val loop = AgentLoop(
                perceiver = service,
                provider = provider,
                actuator = service,
                gate = PolicyGate(),
                confirmer = confirm,
                bus = bus,
                clock = { System.currentTimeMillis() },
                cancelled = { AgentSession.stopRequested },
                undo = undo,
            )
            val result = loop.run(intent)
            Log.i(TAG, "result: $result")
            AgentSession.finish(result)
            onResult(result)
        }
    }

    /** CI / demo: run a fixed scripted plan (no model, no network). */
    fun runScripted(intent: String, script: List<AgentAction>, onResult: (LoopResult) -> Unit = {}) {
        run(intent, ScriptedModelProvider(script), ConfirmationHandler { _, _ -> false }, onResult)
    }
}

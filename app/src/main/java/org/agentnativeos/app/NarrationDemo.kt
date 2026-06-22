package org.agentnativeos.app

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.label
import org.agentnativeos.core.events.Correlation
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.loop.LoopResult
import java.util.concurrent.Executors

/**
 * A canned, self-contained run that drives [AgentSession] with a realistic
 * multi-step intent and pauses at the one high-side-effect step so the confirm
 * card appears. It needs no AccessibilityService and no model — it exists to show
 * and test the narration feed + trust gate. Real runs render through the exact
 * same UI; only the event source differs.
 */
object NarrationDemo {

    private val executor = Executors.newSingleThreadExecutor()
    private const val TASK = "demo"
    private const val INTENT = "tell the team I'm running 10 minutes late"

    fun run() {
        AgentSession.begin()
        executor.execute {
            var step = 0
            fun corr() = Correlation(TASK, step)
            fun now() = System.currentTimeMillis()
            fun pause(ms: Long) = try {
                Thread.sleep(ms)
            } catch (_: InterruptedException) {
            }

            val plan = listOf(
                "Messages" to AgentAction.LaunchApp("Messages"),
                "compose" to AgentAction.Tap("New message"),
                "recipient" to AgentAction.TypeText("To", "Team"),
                "body" to AgentAction.TypeText("Message", "Running 10 minutes late"),
            )

            for ((surface, action) in plan) {
                if (AgentSession.stopRequested) return@execute abort(step)
                AgentSession.emit(NarrationEvent.Perceive(corr(), now(), 24, surface)); pause(450)
                AgentSession.emit(NarrationEvent.Plan(corr(), now(), INTENT)); pause(350)
                AgentSession.emit(NarrationEvent.Propose(corr(), now(), action.label(), highSideEffect = false)); pause(250)
                AgentSession.emit(NarrationEvent.Execute(corr(), now(), action.label(), ok = true)); pause(500)
                step++
            }

            // The one high-side-effect step: sending. The gate forces a confirm.
            if (AgentSession.stopRequested) return@execute abort(step)
            val send = AgentAction.Tap("Send")
            AgentSession.emit(NarrationEvent.Perceive(corr(), now(), 24, "Messages")); pause(400)
            AgentSession.emit(NarrationEvent.Plan(corr(), now(), INTENT)); pause(300)
            AgentSession.emit(NarrationEvent.Propose(corr(), now(), send.label(), highSideEffect = true))

            val approved = AgentSession.awaitConfirmation(send, "Send the message to Team?")
            AgentSession.emit(NarrationEvent.Confirm(corr(), now(), send.label(), approved))
            if (!approved) {
                AgentSession.finish(LoopResult.Aborted("you skipped sending", "drafted the message", step))
                return@execute
            }
            AgentSession.emit(NarrationEvent.Execute(corr(), now(), send.label(), ok = true)); pause(400)
            AgentSession.emit(NarrationEvent.Done(corr(), now(), "texted the team you're running late"))
            AgentSession.finish(LoopResult.Completed("texted the team", step))
        }
    }

    private fun abort(step: Int) {
        val corr = Correlation(TASK, step)
        AgentSession.emit(NarrationEvent.Failure(corr, System.currentTimeMillis(), "stopped by you", "partway through"))
        AgentSession.finish(LoopResult.Aborted("stopped by you", "partway through", step))
    }
}

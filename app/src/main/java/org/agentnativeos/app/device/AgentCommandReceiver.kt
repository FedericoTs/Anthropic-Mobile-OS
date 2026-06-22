package org.agentnativeos.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.agentnativeos.app.AgentController
import org.agentnativeos.core.action.AgentAction

/**
 * adb / demo entry point. For CI it runs a fixed scripted plan against the live
 * screen so the loop + device adapters are exercised end-to-end without a model
 * credential:
 *
 *   adb shell am broadcast -n org.agentnativeos.app/.device.AgentCommandReceiver \
 *     -a org.agentnativeos.app.RUN_SCRIPTED \
 *     --es intent "open battery" --es script "tap:Battery;done:opened"
 */
class AgentCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RUN_SCRIPTED -> {
                val intentText = intent.getStringExtra(EXTRA_INTENT) ?: "scripted run"
                val script = parseScript(intent.getStringExtra(EXTRA_SCRIPT) ?: "")
                Log.i(TAG, "RUN_SCRIPTED intent=\"$intentText\" steps=${script.size}")
                AgentController.runScripted(intentText, script)
            }
            else -> Log.w(TAG, "unknown action ${intent.action}")
        }
    }

    /** Tiny script DSL: "tap:Battery;type:Search=ringtone;done:opened". */
    private fun parseScript(spec: String): List<AgentAction> =
        spec.split(';').mapNotNull { token ->
            val t = token.trim()
            if (t.isEmpty()) return@mapNotNull null
            val parts = t.split(':', limit = 2)
            val op = parts[0]
            val arg = parts.getOrElse(1) { "" }
            when (op) {
                "tap" -> AgentAction.Tap(arg)
                "type" -> arg.split('=', limit = 2)
                    .let { AgentAction.TypeText(it[0], it.getOrElse(1) { "" }) }
                "back" -> AgentAction.Back
                "home" -> AgentAction.Home
                "done" -> AgentAction.Done(arg)
                "abort" -> AgentAction.Abort(arg)
                else -> null
            }
        }

    companion object {
        const val TAG = "AGENT_CMD"
        const val ACTION_RUN_SCRIPTED = "org.agentnativeos.app.RUN_SCRIPTED"
        const val EXTRA_INTENT = "intent"
        const val EXTRA_SCRIPT = "script"
    }
}

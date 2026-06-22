package org.agentnativeos.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.agentnativeos.app.AgentController
import org.agentnativeos.app.AgentSession
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.NarrationDemo
import org.agentnativeos.app.R
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.events.NarrationEvent
import org.agentnativeos.core.loop.ConfirmationHandler
import org.agentnativeos.core.loop.LoopResult
import org.agentnativeos.core.model.Auth
import org.agentnativeos.core.model.ClaudeModelProvider

/**
 * The trust surface: a live vertical timeline of the agent's steps. Done steps
 * settle muted; the active step is the bold coral anchor; the agent's raw
 * "thinking" (plan) lines render in monospace; a high-side-effect step raises an
 * inline confirm card in the thumb zone, and Stop is always reachable.
 */
class NarrationActivity : AppCompatActivity(), AgentSession.Listener {

    private lateinit var timeline: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var confirmCard: View
    private lateinit var confirmAction: TextView
    private var activeRow: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_narration)

        timeline = findViewById(R.id.timeline)
        scroll = findViewById(R.id.scroll)
        confirmCard = findViewById(R.id.confirm_card)
        confirmAction = findViewById(R.id.txt_confirm_action)

        findViewById<TextView>(R.id.txt_intent).text =
            intent.getStringExtra(EXTRA_INTENT)?.let { getString(R.string.narration_intent_prefix, it) } ?: ""

        findViewById<Button>(R.id.btn_approve).setOnClickListener { AgentSession.resolveConfirmation(true) }
        findViewById<Button>(R.id.btn_skip).setOnClickListener { AgentSession.resolveConfirmation(false) }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { AgentSession.requestStop() }
        findViewById<Button>(R.id.btn_apps).setOnClickListener {
            startActivity(Intent(this, AppGridActivity::class.java))
        }

        AgentSession.setListener(this)
        // Render anything already in flight, then start a run if requested.
        AgentSession.snapshot().forEach { addRow(it) }
        when {
            AgentSession.running -> Unit // already in flight; we just render it
            intent.getBooleanExtra(EXTRA_DEMO, false) -> NarrationDemo.run()
            intent.getBooleanExtra(EXTRA_RUN, false) -> startRealRun(intent.getStringExtra(EXTRA_INTENT))
        }
    }

    /** Start a real run against the live screen using the stored model credential. */
    private fun startRealRun(intentText: String?) {
        if (intentText.isNullOrBlank()) return
        val store = CredentialStore(this)
        val auth = Auth.choose(store.apiKey, store.oauthToken) ?: return
        val model = ModelPreferences(this).selected
        AgentController.run(
            intentText,
            ClaudeModelProvider(auth, model),
            ConfirmationHandler { action, reason -> AgentSession.awaitConfirmation(action, reason) },
        )
    }

    override fun onDestroy() {
        AgentSession.setListener(null)
        super.onDestroy()
    }

    // --- AgentSession.Listener (callbacks arrive on the main thread) ---

    override fun onEvent(event: NarrationEvent) = addRow(event)

    override fun onConfirmRequested(action: AgentAction, reason: String) {
        confirmAction.text = reason
        confirmCard.visibility = View.VISIBLE
        scrollToEnd()
    }

    override fun onConfirmResolved() {
        confirmCard.visibility = View.GONE
    }

    override fun onFinished(result: LoopResult) {
        activeRow = null // nothing is "active" once the run ends
    }

    // Narration text is inherently dynamic (the agent's own words + a glyph prefix);
    // the ✓/⚠ markers are status, not translatable copy.
    @SuppressLint("SetTextI18n")
    private fun addRow(event: NarrationEvent) {
        // Settle the previous active step into the muted "done" style.
        activeRow?.apply {
            setTextColor(color(R.color.muted))
            setTypeface(null, Typeface.NORMAL)
        }

        val row = TextView(this).apply {
            textSize = 16f
            val pad = (6 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }

        when (event) {
            is NarrationEvent.Plan -> {
                row.text = event.live()
                row.typeface = Typeface.MONOSPACE
                row.setTextColor(color(R.color.muted))
                row.textSize = 14f
            }
            is NarrationEvent.Done -> {
                row.text = "✓ ${event.live()}"
                row.setTextColor(color(R.color.accent))
                row.setTypeface(null, Typeface.BOLD)
            }
            is NarrationEvent.Failure -> {
                row.text = "⚠ ${event.live()}"
                row.setTextColor(color(R.color.warn))
            }
            else -> {
                row.text = event.live()
                row.setTextColor(color(R.color.text))
            }
        }

        // The newest non-terminal, non-thinking step becomes the active anchor.
        val terminal = event is NarrationEvent.Done || event is NarrationEvent.Failure
        if (!terminal && event !is NarrationEvent.Plan) {
            row.setTextColor(color(R.color.accent))
            row.setTypeface(null, Typeface.BOLD)
            activeRow = row
        } else if (terminal) {
            activeRow = null
        }

        timeline.addView(row)
        scrollToEnd()
    }

    private fun scrollToEnd() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_RUN = "run"
        const val EXTRA_INTENT = "intent"
    }
}

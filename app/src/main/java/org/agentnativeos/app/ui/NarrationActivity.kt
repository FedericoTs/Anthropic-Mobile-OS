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
import org.agentnativeos.app.AutonomyPreferences
import org.agentnativeos.app.CredentialStore
import org.agentnativeos.app.ModelPreferences
import org.agentnativeos.app.NarrationDemo
import org.agentnativeos.app.OverlayConfirm
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
        findViewById<Button>(R.id.btn_undo).setOnClickListener {
            it.visibility = View.GONE // consumed — a run rewinds once
            AgentController.undoLast()
        }

        AgentSession.setListener(this)
        // Render anything already in flight, then start a run if requested.
        AgentSession.snapshot().forEach { if (it !is NarrationEvent.StepTiming) addRow(it) }
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
        val autonomous = AutonomyPreferences(this).autonomous
        // Full silent auto-approve when autonomous (opt-in; the user owns the risk). The
        // gate still CLASSIFIES the action, so the feed still flags it high-side-effect —
        // autonomous mode removes the tap, not the transparency.
        val confirmer = if (autonomous) {
            ConfirmationHandler { _, _ -> true }
        } else {
            ConfirmationHandler { action, reason -> AgentSession.awaitConfirmation(action, reason) }
        }
        if (autonomous) {
            findViewById<TextView>(R.id.txt_intent).apply {
                text = getString(R.string.narration_autonomous)
                setTextColor(ContextCompat.getColor(this@NarrationActivity, R.color.warn))
            }
        }
        AgentController.run(intentText, ClaudeModelProvider(auth, model), confirmer)
    }

    override fun onResume() {
        super.onResume()
        // While our feed is visible, the inline confirm card is enough; the floating
        // overlay is only needed once the agent navigates into another app.
        AgentSession.uiForeground = true
        OverlayConfirm.hide()
    }

    override fun onPause() {
        AgentSession.uiForeground = false
        super.onPause()
    }

    override fun onDestroy() {
        AgentSession.setListener(null)
        super.onDestroy()
    }

    // --- AgentSession.Listener (callbacks arrive on the main thread) ---

    override fun onEvent(event: NarrationEvent) {
        if (event is NarrationEvent.StepTiming) return // instrumentation only
        addRow(event)
    }

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
        // Offer to rewind if the run did anything reversible (the trust gate's other half).
        if (AgentSession.lastUndoStack?.canUndo == true) {
            findViewById<Button>(R.id.btn_undo).visibility = View.VISIBLE
        }
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

        // The RESULT gets its own surface — a calm serif answer card, distinct from the
        // mono thinking lines and step rows (DESIGN: the payoff of "watch it think").
        if (event is NarrationEvent.Done) {
            activeRow = null
            timeline.addView(resultCard(event))
            scrollToEnd()
            return
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

    /** The answer surface: warm card, coral check caption, serif body — the result, not a log line. */
    @SuppressLint("SetTextI18n")
    private fun resultCard(done: NarrationEvent.Done): View {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@NarrationActivity, R.drawable.bg_input)
            setPadding(dp(18), dp(14), dp(18), dp(16))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        card.addView(
            TextView(this).apply {
                text = "✓ ${getString(R.string.result_done)}"
                textSize = 13f
                setTextColor(color(R.color.accent))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        card.addView(
            TextView(this).apply {
                text = done.summary
                textSize = 17f
                typeface = Typeface.SERIF
                setTextColor(color(R.color.text))
                setLineSpacing((4 * resources.displayMetrics.density), 1f)
                setPadding(0, dp(6), 0, 0)
            },
        )
        done.places.forEach { card.addView(placeCard(it)) }
        return card
    }

    /** One actionable place from the answer: name + detail + one-tap Maps / Navigate. */
    private fun placeCard(place: org.agentnativeos.core.action.Place): View {
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@NarrationActivity, R.drawable.bg_pill)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        box.addView(
            TextView(this).apply {
                text = place.name
                textSize = 16f
                setTextColor(color(R.color.text))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        if (place.detail.isNotEmpty()) {
            box.addView(
                TextView(this).apply {
                    text = place.detail
                    textSize = 13f
                    setTextColor(color(R.color.muted))
                    setPadding(0, dp(2), 0, 0)
                },
            )
        }
        val chips = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        chips.addView(chip(getString(R.string.place_open_maps), primary = true) {
            openPlace("geo:0,0?q=${android.net.Uri.encode(place.query)}")
        })
        chips.addView(chip(getString(R.string.place_navigate), primary = false) {
            openPlace("google.navigation:q=${android.net.Uri.encode(place.query)}")
        })
        box.addView(chips)
        return box
    }

    private fun chip(label: String, primary: Boolean, onTap: () -> Unit): View =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            minHeight = 0
            minimumHeight = (40 * resources.displayMetrics.density).toInt()
            if (primary) {
                setTextColor(color(R.color.on_accent))
                backgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.accent))
            } else {
                setTextColor(color(R.color.text))
                background = ContextCompat.getDrawable(this@NarrationActivity, R.drawable.bg_input)
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
            setOnClickListener { onTap() }
        }

    private fun openPlace(uri: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, getString(R.string.place_no_maps), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToEnd() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_RUN = "run"
        const val EXTRA_INTENT = "intent"
    }
}

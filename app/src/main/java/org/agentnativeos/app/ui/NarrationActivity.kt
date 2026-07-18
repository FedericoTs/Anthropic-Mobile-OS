package org.agentnativeos.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
import org.agentnativeos.core.model.ModelCatalog

/**
 * The trust surface (mockup: active-narration.png). The INTENT is the hero in a
 * header card with a live status line; the agent's work renders as coalesced STEP
 * CARDS on a glyph rail — the active step glows soft coral, settled steps show a
 * quiet check — and the answer arrives as the serif result card. The confirm card
 * is the inline gate in the thumb zone; Stop is always reachable in the bottom bar.
 */
class NarrationActivity : AppCompatActivity(), AgentSession.Listener {

    private lateinit var timeline: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var confirmCard: View
    private lateinit var confirmAction: TextView
    private lateinit var txtStatus: TextView

    /** One visual card per loop step (correlation-keyed); events update it in place. */
    private inner class StepCard(val root: View, val glyph: TextView, val card: LinearLayout) {
        val caption: TextView = smallCaps().also { card.addView(it) }
        val title: TextView = TextView(this@NarrationActivity).apply {
            textSize = 16.5f
            typeface = Typeface.SERIF
            setTextColor(color(R.color.text))
            visibility = View.GONE
            card.addView(this)
        }
        val mono: TextView = TextView(this@NarrationActivity).apply {
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextColor(color(R.color.muted))
            setPadding(0, dp(3), 0, 0)
            visibility = View.GONE
            card.addView(this)
        }
        val detail: TextView = TextView(this@NarrationActivity).apply {
            textSize = 13f
            setTextColor(color(R.color.muted))
            setPadding(0, dp(3), 0, 0)
            visibility = View.GONE
            card.addView(this)
        }
    }

    private val cards = LinkedHashMap<String, StepCard>()
    private var activeCard: StepCard? = null
    private var stepsSeen = 0
    private val appLabels = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_narration)

        timeline = findViewById(R.id.timeline)
        scroll = findViewById(R.id.scroll)
        confirmCard = findViewById(R.id.confirm_card)
        confirmAction = findViewById(R.id.txt_confirm_action)
        txtStatus = findViewById(R.id.txt_status)

        findViewById<TextView>(R.id.txt_intent).text = intent.getStringExtra(EXTRA_INTENT) ?: ""
        txtStatus.text = getString(R.string.narration_status_running)

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
        findViewById<TextView>(R.id.txt_model).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        AgentSession.setListener(this)
        // Render anything already in flight, then start a run if requested.
        AgentSession.snapshot().forEach { render(it) }
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
        if (autonomous) txtStatus.text = getString(R.string.narration_autonomous)
        AgentController.run(intentText, ClaudeModelProvider(auth, model), confirmer)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        // While our feed is visible, the inline confirm card is enough; the floating
        // overlay is only needed once the agent navigates into another app.
        AgentSession.uiForeground = true
        OverlayConfirm.hide()
        val model = ModelCatalog.byId(ModelPreferences(this).selected)
        findViewById<TextView>(R.id.txt_model).text =
            getString(R.string.narration_model_pill, model.shortLabel)
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

    override fun onEvent(event: NarrationEvent) = render(event)

    override fun onConfirmRequested(action: AgentAction, reason: String) {
        confirmAction.text = reason
        confirmCard.visibility = View.VISIBLE
        scrollToEnd()
    }

    override fun onConfirmResolved() {
        confirmCard.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    override fun onFinished(result: LoopResult) {
        settleActive()
        txtStatus.text = when (result) {
            is LoopResult.Completed -> "${getString(R.string.narration_status_finished)} · ${stepsSeen} steps"
            is LoopResult.Aborted -> getString(R.string.narration_status_stopped)
        }
        findViewById<TextView>(R.id.txt_live_dot).visibility = View.INVISIBLE
        // Offer to rewind if the run did anything reversible (the trust gate's other half).
        if (AgentSession.lastUndoStack?.canUndo == true) {
            findViewById<Button>(R.id.btn_undo).visibility = View.VISIBLE
        }
    }

    // --- Rendering: events coalesce into per-step cards on a glyph rail ---

    @SuppressLint("SetTextI18n")
    private fun render(event: NarrationEvent) {
        when (event) {
            is NarrationEvent.StepTiming -> return // instrumentation only
            is NarrationEvent.Done -> {
                settleActive()
                timeline.addView(resultCard(event))
            }
            is NarrationEvent.Failure -> {
                settleActive()
                timeline.addView(noteRow("⚠ ${event.live()}", color(R.color.warn)))
            }
            is NarrationEvent.Verify -> {
                timeline.addView(
                    if (event.ok) {
                        noteRow("✓ ${event.live()}", color(R.color.muted))
                    } else {
                        noteRow("↻ ${event.live()}", color(R.color.warn))
                    },
                )
            }
            is NarrationEvent.Confirm -> {
                activeCard?.caption?.apply {
                    text = if (event.approved) "APPROVED" else "SKIPPED"
                    setTextColor(color(if (event.approved) R.color.accent_press else R.color.muted))
                    visibility = View.VISIBLE
                }
            }
            else -> {
                val key = event.correlation.let { "${it.taskId}/${it.stepId}/${it.agentId}" }
                val card = cards.getOrPut(key) { newStepCard() }
                when (event) {
                    is NarrationEvent.Perceive -> {
                        card.detail.text = "Reading ${appLabel(event.pkg)} · ${event.nodeCount} elements"
                        card.detail.visibility = View.VISIBLE
                    }
                    is NarrationEvent.Plan -> {
                        if (card.mono.visibility != View.VISIBLE) {
                            card.mono.text = event.intent.take(90)
                            card.mono.visibility = View.VISIBLE
                        }
                    }
                    is NarrationEvent.Propose -> {
                        card.title.text = event.live()
                        card.title.visibility = View.VISIBLE
                        if (event.highSideEffect) {
                            card.caption.text = getString(R.string.confirm_caution_caps)
                            card.caption.setTextColor(color(R.color.accent_press))
                            card.caption.visibility = View.VISIBLE
                        }
                    }
                    is NarrationEvent.Execute -> {
                        card.title.text = event.live()
                        card.title.visibility = View.VISIBLE
                        settle(card, ok = event.ok)
                    }
                    else -> Unit
                }
            }
        }
        scrollToEnd()
    }

    /** A fresh step card: coral rail dot + soft-coral active card; settles on execute. */
    @SuppressLint("SetTextI18n")
    private fun newStepCard(): StepCard {
        settleActive()
        stepsSeen++
        if (AgentSession.running) {
            txtStatus.text = "${getString(R.string.narration_status_running).trimEnd('…')} · step $stepsSeen"
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        val glyph = TextView(this).apply {
            text = "●"
            textSize = 13f
            setTextColor(color(R.color.accent))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@NarrationActivity, R.drawable.bg_card_active)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(glyph)
        row.addView(card)
        timeline.addView(row)
        val step = StepCard(row, glyph, card).also {
            it.caption.text = getString(R.string.step_active_caps)
            it.caption.setTextColor(color(R.color.accent_press))
            it.caption.visibility = View.VISIBLE
        }
        activeCard = step
        return step
    }

    /** Settle a card into the quiet done style: check glyph, plain surface, no caption. */
    private fun settle(card: StepCard, ok: Boolean = true) {
        card.glyph.text = if (ok) "✓" else "⚠"
        card.glyph.setTextColor(color(if (ok) R.color.accent else R.color.warn))
        card.card.background = ContextCompat.getDrawable(this, R.drawable.bg_input)
        if (card.caption.text == getString(R.string.step_active_caps)) {
            card.caption.visibility = View.GONE
        }
        if (card == activeCard) activeCard = null
    }

    private fun settleActive() {
        activeCard?.let { settle(it) }
    }

    /** A small quiet line between cards (verify notes, failures). */
    private fun noteRow(text: String, tint: Int): View = TextView(this).apply {
        this.text = text
        textSize = 13.5f
        setTextColor(tint)
        setPadding(dp(26), dp(8), 0, 0)
    }

    private fun appLabel(pkg: String?): String {
        if (pkg == null) return "the screen"
        return appLabels.getOrPut(pkg) {
            try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }
        }
    }

    private fun smallCaps(): TextView = TextView(this).apply {
        textSize = 11f
        letterSpacing = 0.08f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(R.color.muted))
        visibility = View.GONE
    }

    /** The answer surface: warm card, coral check caption, serif body — the result, not a log line. */
    @SuppressLint("SetTextI18n")
    private fun resultCard(done: NarrationEvent.Done): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@NarrationActivity, R.drawable.bg_input)
            setPadding(dp(18), dp(14), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
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
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(0, dp(6), 0, 0)
            },
        )
        done.places.forEachIndexed { i, place -> card.addView(placeCard(place, topPick = i == 0)) }
        return card
    }

    /** One actionable place from the answer: name + detail + WHY (top pick) + Maps / Navigate. */
    private fun placeCard(place: org.agentnativeos.core.action.Place, topPick: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(
                this@NarrationActivity,
                if (topPick) R.drawable.bg_card_active else R.drawable.bg_pill,
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        if (topPick) {
            box.addView(
                TextView(this).apply {
                    text = getString(R.string.place_top_pick)
                    textSize = 11f
                    letterSpacing = 0.08f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color(R.color.accent_press))
                    setPadding(0, 0, 0, dp(4))
                },
            )
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
        if (place.why.isNotEmpty()) {
            box.addView(
                TextView(this).apply {
                    text = place.why
                    textSize = 13f
                    setTypeface(typeface, Typeface.ITALIC)
                    setTextColor(color(R.color.muted))
                    setPadding(0, dp(3), 0, 0)
                },
            )
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
            stateListAnimator = null
            minHeight = 0
            minWidth = 0
            minimumHeight = dp(40)
            minimumWidth = dp(96)
            setPadding(dp(16), 0, dp(16), 0)
            background = ContextCompat.getDrawable(
                this@NarrationActivity,
                if (primary) R.drawable.bg_btn_accent else R.drawable.bg_btn_outline,
            )
            setTextColor(color(if (primary) R.color.on_accent else R.color.text))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onTap() }
        }

    private fun openPlace(uri: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                this, getString(R.string.place_no_maps), android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun scrollToEnd() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_RUN = "run"
        const val EXTRA_INTENT = "intent"
    }
}

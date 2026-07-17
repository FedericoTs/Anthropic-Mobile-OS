package org.agentnativeos.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.agentnativeos.app.device.AgentAccessibilityService

/**
 * The floating trust surface, pinned OVER whatever app the agent is driving.
 *
 * Two modes share one non-focusable window in the thumb zone:
 *  - [showStep] — a compact live-step strip (the agent's current action + Stop), so you
 *    can WATCH it think while it acts in another app, not just approve.
 *  - [show] — the confirm card (caution + drafted action + Approve / Skip / Stop), which
 *    takes priority: a pending confirm must never be hidden behind the live strip.
 *
 * NON-FOCUSABLE on purpose: it must not become the active window, or the accessibility
 * actuator would target the overlay instead of the app beneath it. Needs the user-granted
 * "Display over other apps" permission; without it both are no-ops and the in-app feed is
 * the fallback. Buttons route back through [AgentSession], the same path as the in-app UI.
 */
// The held View references a Context, but it is bounded: hide() removes it and nulls the
// reference on every confirm resolution / stop / run finish. Standard overlay-manager
// pattern, not an unbounded static leak.
@SuppressLint("StaticFieldLeak")
object OverlayConfirm {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var windowManager: WindowManager? = null
    private var stepLabel: TextView? = null // the live strip's text, for in-place updates
    private var confirming = false // a confirm card is up; it outranks the live strip

    /** Whether the floating overlay can be shown (special permission granted). */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Stream/refresh the live step. No-op while a confirm card is showing. */
    fun showStep(text: String) {
        val ctx = AgentAccessibilityService.instance ?: return
        if (!canDraw(ctx)) return
        main.post {
            if (confirming) return@post
            stepLabel?.let { it.text = text; return@post } // update in place, no re-add
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val strip = buildStrip(ctx, text)
            try {
                wm.addView(strip, overlayParams(ctx))
                view = strip
                windowManager = wm
            } catch (_: Exception) {
                stepLabel = null // add raced a teardown / revoke — fall back to the in-app feed
            }
        }
    }

    /** Show the confirm card; it replaces the live strip and outranks it until resolved. */
    fun show(reason: String) {
        val ctx = AgentAccessibilityService.instance ?: return
        if (!canDraw(ctx)) return
        main.post {
            confirming = true
            removeCurrent() // drop the live strip if present
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val card = buildCard(ctx, reason)
            try {
                wm.addView(card, overlayParams(ctx))
                view = card
                windowManager = wm
                slideUp(ctx, card)
            } catch (_: Exception) {
                // Permission revoked mid-run, or window add raced a teardown — fall back silently.
            }
        }
    }

    fun hide() {
        main.post {
            removeCurrent()
            windowManager = null
            confirming = false
        }
    }

    /** Remove whatever overlay is showing; caller resets [windowManager]/[confirming]. */
    private fun removeCurrent() {
        val v = view ?: return
        try {
            windowManager?.removeView(v)
        } catch (_: Exception) {
            // Already gone — ignore.
        }
        view = null
        stepLabel = null
    }

    private fun overlayParams(ctx: Context) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable: never steal the active window from the app the agent acts on.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        y = dp(ctx, 24)
    }

    /** DESIGN: confirm slides up from the thumb zone; honor reduced-motion. */
    private fun slideUp(ctx: Context, card: View) {
        val reduceMotion = Settings.Global.getFloat(
            ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
        if (reduceMotion) return
        card.translationY = dp(ctx, 64).toFloat()
        card.alpha = 0f
        card.animate().translationY(0f).alpha(1f).setDuration(300).start()
    }

    /** The live-step strip: the agent's current action (mono, per DESIGN) + a Stop. */
    private fun buildStrip(ctx: Context, text: String): View {
        fun col(id: Int) = ContextCompat.getColor(ctx, id)
        val pad = dp(ctx, 16)
        val label = TextView(ctx).apply {
            this.text = text
            setTextColor(col(R.color.text))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        stepLabel = label
        val stop = Button(ctx).apply {
            this.text = ctx.getString(R.string.narration_stop)
            setTextColor(col(R.color.on_accent))
            backgroundTintList = ColorStateList.valueOf(col(R.color.accent))
            isAllCaps = false
            setOnClickListener { AgentSession.requestStop() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 44),
            ).apply { marginStart = dp(ctx, 12) }
        }
        val strip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input)
            setPadding(pad, dp(ctx, 10), pad, dp(ctx, 10))
            addView(label)
            addView(stop)
        }
        return FrameLayout(ctx).apply {
            setPadding(pad, 0, pad, 0)
            addView(strip)
        }
    }

    private fun buildCard(ctx: Context, reason: String): View {
        fun col(id: Int) = ContextCompat.getColor(ctx, id)
        val pad = dp(ctx, 16)
        val h48 = dp(ctx, 48)

        val caution = TextView(ctx).apply {
            text = ctx.getString(R.string.confirm_caution)
            setTextColor(col(R.color.accent))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        val action = TextView(ctx).apply {
            text = reason
            setTextColor(col(R.color.text))
            textSize = 17f
            setPadding(0, dp(ctx, 6), 0, 0)
        }
        val approve = Button(ctx).apply {
            text = ctx.getString(R.string.confirm_approve)
            setTextColor(col(R.color.on_accent))
            backgroundTintList = ColorStateList.valueOf(col(R.color.accent))
            isAllCaps = false
            setOnClickListener { AgentSession.resolveConfirmation(true) }
            layoutParams = LinearLayout.LayoutParams(0, h48, 1f)
        }
        val skip = Button(ctx).apply {
            text = ctx.getString(R.string.confirm_skip)
            setTextColor(col(R.color.text))
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_pill)
            isAllCaps = false
            setOnClickListener { AgentSession.resolveConfirmation(false) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, h48,
            ).apply { marginStart = dp(ctx, 8) }
        }
        val stop = Button(ctx).apply {
            text = ctx.getString(R.string.narration_stop)
            setTextColor(col(R.color.on_accent))
            backgroundTintList = ColorStateList.valueOf(col(R.color.accent))
            isAllCaps = false
            setOnClickListener { AgentSession.requestStop() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, h48,
            ).apply { marginStart = dp(ctx, 8) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 14), 0, 0)
            addView(approve)
            addView(skip)
            addView(stop)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_input)
            setPadding(pad, pad, pad, pad)
            addView(caution)
            addView(action)
            addView(row)
        }
        return FrameLayout(ctx).apply {
            setPadding(pad, 0, pad, 0)
            addView(card)
        }
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}

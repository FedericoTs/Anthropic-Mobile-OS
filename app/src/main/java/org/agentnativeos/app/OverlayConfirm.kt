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
 * The confirm gate, floated OVER whatever app the agent is driving.
 *
 * When the agent acts in a third-party app (e.g. tapping Send in Gmail), our own
 * narration screen is backgrounded — so a high-side-effect confirm would surface
 * where the user can't see it, exactly when it matters most. This puts the same
 * trust card (caution + drafted action + Approve / Skip / Stop) in a small floating
 * window pinned to the thumb zone, on top of the foreground app.
 *
 * It is NON-FOCUSABLE on purpose: it must not become the active window, or the
 * accessibility actuator would target the overlay instead of the app beneath it.
 * Needs the user-granted "Display over other apps" permission; without it [show] is
 * a no-op and the in-app confirm card remains the fallback. Resolving routes back
 * through [AgentSession], the same path the in-app buttons use.
 */
// The held View references a Context, but it is bounded: hide() removes it and nulls
// the reference on every confirm resolution / stop / run finish, so it lives only for
// the duration of a pending confirm (by design). This is the standard overlay-manager
// pattern, not an unbounded static leak.
@SuppressLint("StaticFieldLeak")
object OverlayConfirm {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var windowManager: WindowManager? = null

    /** Whether the floating confirm can be shown (special permission granted). */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(reason: String) {
        val ctx = AgentAccessibilityService.instance ?: return
        if (!canDraw(ctx)) return // graceful fallback to the in-app card
        main.post {
            if (view != null) return@post
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val card = buildCard(ctx, reason)
            val lp = WindowManager.LayoutParams(
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
            try {
                wm.addView(card, lp)
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
            val v = view ?: return@post
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
                // Already gone — ignore.
            }
            view = null
            windowManager = null
        }
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

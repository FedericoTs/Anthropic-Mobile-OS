package org.agentnativeos.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.agentnativeos.app.Capabilities
import org.agentnativeos.app.intentFor
import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.ScrollDirection
import org.agentnativeos.core.loop.ActionOutcome
import org.agentnativeos.core.loop.Actuator
import org.agentnativeos.core.loop.Perceiver
import org.agentnativeos.core.perception.Observation

/**
 * The on-device agent: it is both the [Perceiver] (reads rootInActiveWindow into
 * a core Observation) and the [Actuator] (turns typed AgentActions into real
 * taps / text entry / launches), so the same AgentLoop that is unit-tested with
 * fakes runs unchanged against a live phone.
 */
class AgentAccessibilityService : AccessibilityService(), Perceiver, Actuator {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The loop drives perception on demand; we don't react to events here.
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // --- Perceiver ---
    override fun perceive(): Observation {
        val root = rootInActiveWindow
        return Observation(
            rootPackage = root?.packageName?.toString(),
            root = root?.let { AccessibilityScreenNode(it) },
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    // --- Actuator ---
    override fun act(action: AgentAction): ActionOutcome = when (action) {
        is AgentAction.Tap -> tap(action.targetQuery)
        is AgentAction.TypeText -> type(action.targetQuery, action.value)
        is AgentAction.Scroll -> scroll(action.targetQuery, action.direction)
        is AgentAction.Invoke -> invoke(action.capability, action.args)
        is AgentAction.LaunchApp -> launch(action.packageName)
        AgentAction.Back -> ActionOutcome(performGlobalAction(GLOBAL_ACTION_BACK))
        AgentAction.Home -> ActionOutcome(performGlobalAction(GLOBAL_ACTION_HOME))
        is AgentAction.Done, is AgentAction.Abort -> ActionOutcome(true) // terminal; loop handles
    }

    private fun findNode(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()
            val d = n.contentDescription?.toString()
            val id = n.viewIdResourceName
            if (t?.contains(query, ignoreCase = true) == true ||
                d?.contains(query, ignoreCase = true) == true ||
                id?.contains(query, ignoreCase = true) == true
            ) {
                return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun tap(query: String): ActionOutcome {
        val node = findNode(query) ?: return ActionOutcome(false, "no node matching \"$query\"")
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ActionOutcome(true, "click")
        }
        val b = Rect().also { node.getBoundsInScreen(it) }
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return ActionOutcome(dispatchGesture(gesture, null, null), "gesture")
    }

    private fun scroll(query: String, direction: ScrollDirection): ActionOutcome {
        val node = findNode(query) ?: return ActionOutcome(false, "no node matching \"$query\"")
        // Prefer a precise accessibility scroll on the nearest scrollable container
        // (wheel pickers and lists expose this); fall back to a swipe gesture.
        var scrollable: AccessibilityNodeInfo? = node
        while (scrollable != null && !scrollable.isScrollable) scrollable = scrollable.parent
        if (scrollable != null) {
            val act = if (direction == ScrollDirection.UP) {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (scrollable.performAction(act)) return ActionOutcome(true, "scroll")
        }
        val b = Rect().also { node.getBoundsInScreen(it) }
        val x = b.exactCenterX()
        val near = b.top + b.height() * 0.25f
        val far = b.top + b.height() * 0.75f
        val (startY, endY) = if (direction == ScrollDirection.UP) far to near else near to far
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 200L))
            .build()
        return ActionOutcome(dispatchGesture(gesture, null, null), "swipe")
    }

    private fun type(query: String, value: String): ActionOutcome {
        val node = findNode(query) ?: return ActionOutcome(false, "no field matching \"$query\"")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return ActionOutcome(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "setText")
    }

    /** Fire a named capability as a real Android Intent (the direct, fast path). */
    private fun invoke(capability: String, args: Map<String, String>): ActionOutcome {
        // Log only the arg KEYS, never values — a message body/recipient is user content.
        Log.i(TAG, "invoke $capability args=${args.keys}")
        val plan = Capabilities.plan(capability, args)
            ?: return ActionOutcome(false, "unknown or invalid capability \"$capability\"")
        return try {
            startActivity(intentFor(plan))
            ActionOutcome(true, "invoke $capability")
        } catch (e: Exception) {
            ActionOutcome(false, "no app handles $capability (${e.message})")
        }
    }

    private fun launch(pkg: String): ActionOutcome {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionOutcome(false, "no launch intent for $pkg")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // Agent-driven opens count toward the predictive "Apps, right now" row too.
        org.agentnativeos.app.AppLaunchLog(this).record(pkg)
        return ActionOutcome(true, "launch")
    }

    companion object {
        const val TAG = "AGENT_SVC"

        @Volatile
        var instance: AgentAccessibilityService? = null
    }
}

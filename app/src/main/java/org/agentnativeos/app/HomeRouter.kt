package org.agentnativeos.app

/** What the home screen should do with a typed intent. */
sealed interface HomeAction {
    data class OpenApp(val query: String) : HomeAction
    data class RunAgent(val intent: String) : HomeAction
    data object ShowDemo : HomeAction
    data object NeedService : HomeAction
    data object NeedModel : HomeAction
    data object Ignore : HomeAction
}

/**
 * Pure routing for the home screen, so the decision logic is unit-tested instead
 * of buried in the Activity. "open X" and "demo" are escape hatches that work
 * with no agent; a real intent needs both the AccessibilityService and a model.
 */
object HomeRouter {

    fun route(rawIntent: String, hasModel: Boolean, serviceEnabled: Boolean): HomeAction {
        val intent = rawIntent.trim()
        if (intent.isEmpty()) return HomeAction.Ignore
        if (intent.equals("demo", ignoreCase = true)) return HomeAction.ShowDemo
        if (intent.startsWith("open ", ignoreCase = true)) {
            // Only a bare "open <app>" is the fast launcher escape hatch. A task
            // that merely begins with "open" (e.g. "open clock and start a timer")
            // must fall through to the agent, not be mistaken for an app name.
            appLaunchQuery(intent.substring(5))?.let { return HomeAction.OpenApp(it) }
        }
        if (!serviceEnabled) return HomeAction.NeedService
        if (!hasModel) return HomeAction.NeedModel
        return HomeAction.RunAgent(intent)
    }

    /** The app-name in a bare "open X", or null if X looks like a multi-step task. */
    private fun appLaunchQuery(rest: String): String? {
        val query = rest.trim()
        if (query.isEmpty()) return null
        val lower = query.lowercase()
        // A follow-on clause ("...and start a timer") means this is an agent task.
        val hasFollowOn = listOf(" and ", " then ", " & ", ",", ";").any { it in lower }
        if (hasFollowOn) return null
        // An app name is short; a sentence is not.
        if (query.split(Regex("\\s+")).size > 3) return null
        return query
    }
}

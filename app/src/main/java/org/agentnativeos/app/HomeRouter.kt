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
            return HomeAction.OpenApp(intent.substring(5).trim())
        }
        if (!serviceEnabled) return HomeAction.NeedService
        if (!hasModel) return HomeAction.NeedModel
        return HomeAction.RunAgent(intent)
    }
}

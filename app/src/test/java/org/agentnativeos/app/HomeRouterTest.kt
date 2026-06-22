package org.agentnativeos.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRouterTest {

    @Test
    fun emptyIsIgnored() {
        assertEquals(HomeAction.Ignore, HomeRouter.route("   ", hasModel = true, serviceEnabled = true))
    }

    @Test
    fun demoKeywordShowsDemo() {
        assertEquals(HomeAction.ShowDemo, HomeRouter.route("demo", hasModel = false, serviceEnabled = false))
        assertEquals(HomeAction.ShowDemo, HomeRouter.route("DEMO", hasModel = false, serviceEnabled = false))
    }

    @Test
    fun openLaunchesAppRegardlessOfModelOrService() {
        assertEquals(
            HomeAction.OpenApp("Settings"),
            HomeRouter.route("open Settings", hasModel = false, serviceEnabled = false),
        )
    }

    @Test
    fun realIntentNeedsServiceThenModel() {
        assertEquals(HomeAction.NeedService, HomeRouter.route("text mom", hasModel = true, serviceEnabled = false))
        assertEquals(HomeAction.NeedModel, HomeRouter.route("text mom", hasModel = false, serviceEnabled = true))
    }

    @Test
    fun realIntentRunsAgentWhenReady() {
        assertEquals(
            HomeAction.RunAgent("text mom I'm late"),
            HomeRouter.route("  text mom I'm late ", hasModel = true, serviceEnabled = true),
        )
    }
}

package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionJsonTest {

    @Test
    fun parsesEveryActionType() {
        assertEquals(AgentAction.Tap("Battery"), ActionJson.parse("""{"action":"tap","target":"Battery"}"""))
        assertEquals(
            AgentAction.TypeText("Search", "ringtone"),
            ActionJson.parse("""{"action":"type","target":"Search","text":"ringtone"}"""),
        )
        assertEquals(
            AgentAction.LaunchApp("com.android.settings"),
            ActionJson.parse("""{"action":"launch","package":"com.android.settings"}"""),
        )
        assertEquals(
            AgentAction.Scroll("minutes", ScrollDirection.UP),
            ActionJson.parse("""{"action":"scroll","target":"minutes","direction":"up"}"""),
        )
        assertEquals(
            AgentAction.Scroll("list", ScrollDirection.DOWN),
            ActionJson.parse("""{"action":"scroll","target":"list","direction":"DOWN"}"""),
        )
        assertNull(ActionJson.parse("""{"action":"scroll","target":"x","direction":"sideways"}"""))
        assertEquals(AgentAction.Back, ActionJson.parse("""{"action":"back"}"""))
        assertEquals(AgentAction.Home, ActionJson.parse("""{"action":"home"}"""))
        assertEquals(AgentAction.Done("opened"), ActionJson.parse("""{"action":"done","summary":"opened"}"""))
        assertEquals(AgentAction.Abort("nope"), ActionJson.parse("""{"action":"abort","reason":"nope"}"""))
    }

    @Test
    fun toleratesSurroundingProseAndWhitespace() {
        val raw = "Sure, here is the next step:\n  {\"action\": \"tap\", \"target\": \"Wi-Fi\"}  \nLet me know."
        assertEquals(AgentAction.Tap("Wi-Fi"), ActionJson.parse(raw))
    }

    @Test
    fun handlesEscapesInValues() {
        val a = ActionJson.parse("""{"action":"type","target":"note","text":"line1\nline2 \"q\""}""")
        assertTrue(a is AgentAction.TypeText)
        assertEquals("line1\nline2 \"q\"", (a as AgentAction.TypeText).value)
    }

    @Test
    fun returnsNullOnGarbageOrUnknownAction() {
        assertNull(ActionJson.parse("not json at all"))
        assertNull(ActionJson.parse("""{"action":"explode"}"""))
        assertNull(ActionJson.parse("""{"action":"tap"}""")) // missing target
        assertNull(ActionJson.parse(""))
    }
}

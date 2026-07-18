package org.agentnativeos.core.model

import org.agentnativeos.core.action.AgentAction
import org.agentnativeos.core.action.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionJsonTest {

    @Test
    fun parsesADoneCarryingStructuredPlaces() {
        val raw = """{"action":"done","summary":"Found two spots.","places":[
            {"name":"Buca di Beppo","detail":"4.4★ · 5.9 mi","query":"Buca di Beppo Italian Restaurant","why":"highest rated open now"},
            {"name":"Azzurro","detail":"","query":""},
            {"name":"  "},
            {"name":"Extra1"},{"name":"Extra2"}
        ]}"""
        val done = ActionJson.parse(raw) as org.agentnativeos.core.action.AgentAction.Done
        assertEquals("Found two spots.", done.summary)
        assertEquals(3, done.places.size) // blank-name dropped, capped at 3
        assertEquals("Buca di Beppo", done.places[0].name)
        assertEquals("4.4★ · 5.9 mi", done.places[0].detail)
        assertEquals("Buca di Beppo Italian Restaurant", done.places[0].query)
        assertEquals("highest rated open now", done.places[0].why)
        assertEquals("non-pick places default to no why", "", done.places[1].why)
        assertEquals("empty query falls back to the name", "Azzurro", done.places[1].query)
        // A plain done still parses through the flat path with no places.
        assertEquals(
            AgentAction.Done("ok"),
            ActionJson.parse("""{"action":"done","summary":"ok"}"""),
        )
    }

    @Test
    fun parseGoals_readsADecomposition() {
        assertEquals(
            listOf("find a pizzeria nearby", "text the address to Marco"),
            ActionJson.parseGoals("""{"goals":["find a pizzeria nearby","text the address to Marco"]}"""),
        )
        // Tolerates surrounding prose; drops blanks; caps at 3.
        assertEquals(
            listOf("a", "b", "c"),
            ActionJson.parseGoals("""ok: {"goals":["a","","b","c","d"]} done"""),
        )
        assertNull(ActionJson.parseGoals("not json"))
        assertNull(ActionJson.parseGoals("""{"goals":[]}"""))
        assertNull(ActionJson.parseGoals("""{"other":"x"}"""))
    }

    @Test
    fun parseVerdict_readsTheCompletionCheck() {
        val yes = ActionJson.parseVerdict("""{"verified": true, "reason": "compose closed"}""")!!
        assertEquals(true, yes.verified)
        assertEquals("compose closed", yes.reason)
        // Tolerates surrounding prose, like the action parser.
        val no = ActionJson.parseVerdict("""here: {"verified": false, "reason": "still open"} .""")!!
        assertEquals(false, no.verified)
        assertEquals("still open", no.reason)
        // No clear verdict -> null so the caller fails open (accepts the done).
        assertNull(ActionJson.parseVerdict("not json"))
        assertNull(ActionJson.parseVerdict("""{"reason":"missing the flag"}"""))
    }

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
        assertEquals(
            AgentAction.Invoke("set_timer", mapOf("seconds" to "300", "message" to "tea")),
            ActionJson.parse("""{"action":"invoke","capability":"set_timer","args":{"seconds":300,"message":"tea"}}"""),
        )
        assertNull(ActionJson.parse("""{"action":"invoke","args":{"x":"y"}}""")) // missing capability
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

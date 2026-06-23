package org.agentnativeos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure capability -> Intent-plan mapping (no Android Intent construction here). */
class CapabilitiesTest {

    @Test
    fun setTimerCarriesSecondsAndStartsImmediately() {
        val plan = Capabilities.plan("set_timer", mapOf("seconds" to "300"))!!
        assertEquals("android.intent.action.SET_TIMER", plan.action)
        assertEquals(300, plan.extras["android.intent.extra.alarm.LENGTH"])
        assertEquals(true, plan.extras["android.intent.extra.alarm.SKIP_UI"])
    }

    @Test
    fun openUrlNormalizesScheme() {
        assertEquals("https://example.com", Capabilities.plan("open_url", mapOf("url" to "example.com"))!!.data)
        assertEquals("https://x.com", Capabilities.plan("open_url", mapOf("url" to "https://x.com"))!!.data)
    }

    @Test
    fun dialAndSmsBuildUris() {
        assertEquals("tel:123", Capabilities.plan("dial", mapOf("number" to "123"))!!.data)
        val sms = Capabilities.plan("send_sms", mapOf("number" to "123", "body" to "hi"))!!
        assertEquals("smsto:123", sms.data)
        assertEquals("hi", sms.extras["sms_body"])
    }

    @Test
    fun sendEmailEncodesSubjectAndBodyIntoTheMailtoUri() {
        // Gmail ignores EXTRA_SUBJECT/EXTRA_TEXT on a bare mailto: SENDTO, so the
        // subject/body must live in the URI query (RFC 6068 — spaces as %20, not '+'),
        // or the compose opens blank and "send" delivers an empty email.
        val plan = Capabilities.plan(
            "send_email",
            mapOf("to" to "a@b.com", "subject" to "Excited for the OS", "body" to "we are excited!"),
        )!!
        assertEquals("android.intent.action.SENDTO", plan.action)
        assertEquals(
            "mailto:a@b.com?subject=Excited%20for%20the%20OS&body=we%20are%20excited%21",
            plan.data,
        )
        assertEquals(true, plan.extras.isEmpty())

        // No subject/body -> plain mailto: (still resolves for capability discovery).
        assertEquals("mailto:a@b.com", Capabilities.plan("send_email", mapOf("to" to "a@b.com"))!!.data)
    }

    @Test
    fun unknownCapabilityOrMissingArgsIsNull() {
        assertNull(Capabilities.plan("teleport", emptyMap()))
        assertNull(Capabilities.plan("set_timer", emptyMap())) // seconds required
        assertNull(Capabilities.plan("set_timer", mapOf("seconds" to "abc"))) // not an int
        assertNull(Capabilities.plan("dial", emptyMap())) // number required
    }

    @Test
    fun catalogIsAdvertisedToThePlanner() {
        val names = Capabilities.CATALOG.map { it.name }
        assertEquals(true, "set_timer" in names && "open_url" in names && "dial" in names)
    }

    @Test
    fun everyCatalogEntryHasAProbeIntent() {
        // Discovery can only filter what it can build a probe for.
        Capabilities.CATALOG.forEach { cap ->
            val plan = Capabilities.probePlan(cap.name)
            assertNotNull("no probe for ${cap.name}", plan)
            assertEquals(true, plan!!.action.isNotBlank())
        }
    }

    @Test
    fun availableFromKeepsOnlyResolvedCapabilities() {
        val available = Capabilities.availableFrom(setOf("set_timer", "dial"))
        assertEquals(listOf("set_timer", "dial"), available.map { it.name })
    }

    @Test
    fun appDeepLinksBuildTheRightUris() {
        assertEquals("geo:0,0?q=Rome", Capabilities.plan("maps", mapOf("query" to "Rome"))!!.data)
        assertEquals(
            "https://www.youtube.com/results?search_query=lofi+beats",
            Capabilities.plan("youtube_search", mapOf("query" to "lofi beats"))!!.data,
        )
        assertEquals(
            "https://wa.me/391234?text=hi",
            Capabilities.plan("whatsapp_message", mapOf("number" to "+39 1234", "text" to "hi"))!!.data,
        )
    }

    @Test
    fun calendarShareAndCameraBuildCorrectPlans() {
        val event = Capabilities.plan("create_event", mapOf("title" to "Standup", "location" to "Room 4"))!!
        assertEquals("android.intent.action.INSERT", event.action)
        assertEquals("content://com.android.calendar/events", event.data)
        assertEquals("Standup", event.extras["title"])
        assertEquals("Room 4", event.extras["eventLocation"])

        val share = Capabilities.plan("share_text", mapOf("text" to "hello"))!!
        assertEquals("text/plain", share.type)
        assertEquals("hello", share.extras["android.intent.extra.TEXT"])

        // Camera needs no args.
        assertEquals("android.media.action.STILL_IMAGE_CAMERA", Capabilities.plan("open_camera", emptyMap())!!.action)
    }

    @Test
    fun appSpecificCapabilitiesDeclareTheirRequiredPackage() {
        assertEquals("com.whatsapp", Capabilities.requiredPackage("whatsapp_message"))
        assertEquals("com.spotify.music", Capabilities.requiredPackage("spotify_search"))
        // Generic + system capabilities need no specific app.
        assertNull(Capabilities.requiredPackage("set_timer"))
        assertNull(Capabilities.requiredPackage("maps"))
    }
}

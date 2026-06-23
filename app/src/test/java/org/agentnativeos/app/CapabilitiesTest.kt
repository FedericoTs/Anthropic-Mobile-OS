package org.agentnativeos.app

import org.junit.Assert.assertEquals
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
}

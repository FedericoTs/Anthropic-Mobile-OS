package org.agentnativeos.core.predict

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionFeedbackCodecTest {

    @Test
    fun roundTripsFeedback() {
        val fb = SuggestionFeedback(
            perIntent = mapOf(
                "start a 25 minute timer" to IntentFeedback(
                    dismissedAtMs = listOf(1_700_000_000_000L, 1_700_100_000_000L),
                    shown = 4,
                    tapped = 1,
                ),
                "check the weather" to IntentFeedback(shown = 2),
            ),
            totalShown = 6,
            totalTapped = 1,
        )
        val decoded = SuggestionFeedbackCodec.decode(SuggestionFeedbackCodec.encode(fb))
        assertEquals(fb, decoded)
    }

    @Test
    fun emptyOrGarbageDecodesToEmptyFeedback() {
        assertEquals(SuggestionFeedback(), SuggestionFeedbackCodec.decode("{}"))
        assertEquals(SuggestionFeedback(), SuggestionFeedbackCodec.decode("not json"))
    }
}

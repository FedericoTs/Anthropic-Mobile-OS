package org.agentnativeos.core.predict

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class UsageTest {

    @Test
    fun timeBucketsMapHoursToSlices() {
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(2))
        assertEquals(TimeBucket.MORNING, TimeBucket.of(9))
        assertEquals(TimeBucket.MIDDAY, TimeBucket.of(12))
        assertEquals(TimeBucket.AFTERNOON, TimeBucket.of(15))
        assertEquals(TimeBucket.EVENING, TimeBucket.of(20))
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(23))
    }

    @Test
    fun normalizeCollapsesCaseWhitespaceAndTrailingPunctuation() {
        assertEquals("set a timer", normalizeIntent("  Set   a Timer. "))
        assertEquals(normalizeIntent("Open Gmail"), normalizeIntent("open gmail"))
        // Distinct specifics stay distinct (v1 is exact-after-normalization).
        assert(normalizeIntent("timer for 25 min") != normalizeIntent("timer for 20 min"))
    }

    @Test
    fun derivesHourAndDayClassFromATimestamp() {
        val zone = ZoneId.of("UTC")
        // 2000-01-01 was a Saturday; 2000-01-03 a Monday (fixed reference, not circular).
        val sat = LocalDateTime.of(2000, 1, 1, 20, 0).atZone(zone).toInstant().toEpochMilli()
        val mon = LocalDateTime.of(2000, 1, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()

        val satEvent = UsageEvent.at("x", sat, zone)
        assertEquals(20, satEvent.hourOfDay)
        assertEquals(DayClass.WEEKEND, satEvent.dayClass)
        assertEquals(TimeBucket.EVENING, satEvent.bucket)

        val monCtx = NowContext.at(mon, zone)
        assertEquals(9, monCtx.hourOfDay)
        assertEquals(DayClass.WEEKDAY, monCtx.dayClass)
        assertEquals(TimeBucket.MORNING, monCtx.bucket)
    }
}

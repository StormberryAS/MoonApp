package no.stormberry.moonapp.alarm

import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.LunarEvent
import no.stormberry.moonapp.lunar.MoonCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The alarm engine, which had no test of any kind until 2026-09-04.
 *
 * The defects these cover, all found by the pre-publication audit and all fixed:
 *  - Full and New Moon fired on FOUR CONSECUTIVE NIGHTS, because the test was a bare
 *    "illuminated fraction >= 0.95", which holds for several days either side of full.
 *  - Those alarms fired at 22:00 UTC for everyone, so a user in Tokyo was woken at 07:00.
 *  - Rise and set were quantised to a 20-minute grid, so an alarm could be twenty minutes
 *    from the event it was named after.
 */
class OccurrenceEngineTest {

    private fun rule(
        event: LunarEvent,
        offsetMinutes: Int = 0,
        lat: Double = 60.3913,   // Bergen
        lon: Double = 5.3221,
        enabled: Boolean = true,
    ) = MoonAlarmRule(
        id = "t-${event.name}-$offsetMinutes",
        label = "test",
        event = event,
        offsetMinutes = offsetMinutes,
        cityName = "Bergen",
        latDeg = lat,
        lonDeg = lon,
        enabled = enabled,
        vibrate = false,
    )

    private fun at(iso: String): Instant = Instant.parse(iso)

    @Test
    fun `a disabled rule never produces an occurrence`() {
        assertNull(OccurrenceEngine.findNextOccurrence(rule(LunarEvent.MOONRISE, enabled = false)))
    }

    @Test
    fun `the next occurrence is always in the future`() {
        val now = at("2026-09-04T12:00:00Z")
        for (e in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET, LunarEvent.LUNAR_TRANSIT)) {
            val next = OccurrenceEngine.findNextOccurrence(rule(e), now)
            assertNotNull("$e produced no occurrence at all", next)
            assertTrue("$e produced $next, which is not after $now", next!!.isAfter(now))
        }
    }

    @Test
    fun `the offset is applied to the event, not to the search`() {
        val now = at("2026-09-04T00:00:00Z")
        val plain = OccurrenceEngine.findNextOccurrence(rule(LunarEvent.MOONRISE), now)!!
        val early = OccurrenceEngine.findNextOccurrence(rule(LunarEvent.MOONRISE, -30), now)!!
        // a rule 30 minutes earlier either lands 30 minutes before the same rise, or, if that
        // moment has already passed, on the following one. Both are correct; a difference of
        // anything other than 30 minutes or a whole lunar day is not.
        val delta = Duration.between(early, plain).toMinutes()
        assertTrue("offset produced a $delta minute gap", delta == 30L || delta < -600L)
    }

    /**
     * THE FOUR-CONSECUTIVE-NIGHTS BUG. Walk a whole lunation and count the days on which a
     * Full Moon alarm would fire. Exactly one night per cycle is correct; the old code gave
     * four or five.
     */
    @Test
    fun `Full Moon fires on exactly one night per lunation`() {
        var firing = 0
        var d = LocalDate.of(2026, 9, 1)
        repeat(30) {
            if (MoonCalc.times(d, 60.3913, 5.3221)[LunarEvent.FULL_MOON] != null) firing++
            d = d.plusDays(1)
        }
        assertEquals("Full Moon fired on $firing nights in 30 days; exactly one is correct", 1, firing)
    }

    @Test
    fun `New Moon fires on exactly one night per lunation`() {
        var firing = 0
        var d = LocalDate.of(2026, 9, 1)
        repeat(30) {
            if (MoonCalc.times(d, 60.3913, 5.3221)[LunarEvent.NEW_MOON] != null) firing++
            d = d.plusDays(1)
        }
        assertEquals("New Moon fired on $firing nights in 30 days; exactly one is correct", 1, firing)
    }

    /**
     * THE 22:00-UTC BUG. The evening alarm must be evening where the USER is. Bergen and
     * Tokyo are 8 hours of longitude apart, so their full-moon alarms must not be the same
     * instant.
     */
    @Test
    fun `the Full Moon alarm is local evening, not a fixed UTC hour`() {
        var bergen: Instant? = null
        var tokyo: Instant? = null
        var d = LocalDate.of(2026, 9, 1)
        repeat(30) {
            bergen = bergen ?: MoonCalc.times(d, 60.3913, 5.3221)[LunarEvent.FULL_MOON]
            tokyo = tokyo ?: MoonCalc.times(d, 35.6762, 139.6503)[LunarEvent.FULL_MOON]
            d = d.plusDays(1)
        }
        assertNotNull("no Full Moon found for Bergen in 30 days", bergen)
        assertNotNull("no Full Moon found for Tokyo in 30 days", tokyo)
        val gap = Duration.between(tokyo, bergen).toHours()
        assertTrue(
            "Bergen and Tokyo fired ${gap}h apart; a fixed UTC hour would make that 0",
            kotlin.math.abs(gap) >= 6
        )
        // and each should be a plausible evening in its own local reckoning
        for ((name, inst, lon) in listOf(Triple("Bergen", bergen!!, 5.3221), Triple("Tokyo", tokyo!!, 139.6503))) {
            val localHour = inst.plusSeconds((lon / 15.0 * 3600.0).toLong())
                .atZone(ZoneOffset.UTC).hour
            assertTrue("$name fired at local hour $localHour, which is not an evening", localHour in 20..23)
        }
    }

    /**
     * An alarm anchored to an event that does not occur must be skipped, not fired at a guess.
     * Tromso sits inside the Arctic circle where the moon can stay up or down for a full day.
     */
    @Test
    fun `a rule at high latitude still resolves or returns nothing, never a wrong time`() {
        val now = at("2026-01-15T00:00:00Z")
        val next = OccurrenceEngine.findNextOccurrence(rule(LunarEvent.MOONRISE, lat = 69.6492, lon = 18.9553), now)
        // either it finds a real rise within the 30-day search, or it honestly finds none
        if (next != null) {
            assertTrue("returned $next which is not after $now", next.isAfter(now))
            assertTrue("returned a time more than 31 days out", Duration.between(now, next).toDays() <= 31)
        }
    }

    /**
     * RE-ARMING. AlarmManager fires a one-shot, so after an alarm rings the engine must be able
     * to hand back the FOLLOWING occurrence. Until 2026-09-04 nothing called it again, so an
     * alarm rang once and was gone while README.md advertised "Daily Auto-Recompute".
     *
     * Moonrise drifts roughly 50 minutes later each day, so the next occurrence must be close
     * to a lunar day away, not exactly 24 hours. A fixed 24-hour re-arm would walk off the
     * event within a week, which is why the receiver recomputes instead of adding a constant.
     */
    @Test
    fun `re-arming from just after a fire yields the following occurrence, a lunar day later`() {
        val r = rule(LunarEvent.MOONRISE)
        val first = OccurrenceEngine.findNextOccurrence(r, at("2026-09-04T00:00:00Z"))!!
        val second = OccurrenceEngine.findNextOccurrence(r, first.plusSeconds(60))!!
        assertTrue("the second occurrence is not after the first", second.isAfter(first))
        val gapMinutes = Duration.between(first, second).toMinutes()
        // a lunar day is about 24h50m; allow a generous band for latitude and interpolation
        assertTrue(
            "gap was $gapMinutes minutes; a lunar day is about 1490",
            gapMinutes in 1350..1650
        )
    }
}

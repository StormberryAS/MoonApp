package no.stormberry.moonapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When the widgets redraw next (1.0.8): never later than three hours away, and at the place's
 * midnight (plus one second) when that comes sooner, on days of 23 and 25 hours and in zones with
 * odd offsets too.
 */
class WidgetScheduleTest {

    private val zones = listOf(
        "UTC", "Europe/Oslo", "Pacific/Kiritimati", "Australia/Lord_Howe", "America/St_Johns",
        "America/Santiago", "Asia/Kolkata", "Pacific/Pago_Pago",
    ).map { ZoneId.of(it) }

    @Test
    fun `the next refresh is always after now and within three hours`() {
        for (zone in zones) {
            var t = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2027-01-01T00:00:00Z")
            while (t.isBefore(end)) {
                val next = WidgetSchedule.nextRefresh(t, zone)
                assertTrue("$zone $t -> $next", next.isAfter(t))
                assertTrue("$zone $t -> $next", !next.isAfter(t.plus(Duration.ofHours(3))))
                // Never past the place's next midnight plus one second.
                val midnight = t.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(1)
                assertTrue("$zone $t -> $next", !next.isAfter(midnight))
                t = t.plus(Duration.ofMinutes(37))
            }
        }
    }

    private fun at(local: String, zone: String): Instant =
        LocalDateTime.parse(local).atZone(ZoneId.of(zone)).toInstant()

    @Test
    fun `it lands one second after the place's midnight when that is under three hours away`() {
        assertEquals(at("2026-10-31T00:00:01", "Europe/Oslo"), WidgetSchedule.nextRefresh(at("2026-10-30T22:15", "Europe/Oslo"), ZoneId.of("Europe/Oslo")))
        assertEquals(at("2026-10-30T23:00", "Europe/Oslo"), WidgetSchedule.nextRefresh(at("2026-10-30T20:00", "Europe/Oslo"), ZoneId.of("Europe/Oslo")))
        // UTC+14 and UTC-3:30.
        assertEquals(at("2026-10-31T00:00:01", "Pacific/Kiritimati"), WidgetSchedule.nextRefresh(at("2026-10-30T23:59:59", "Pacific/Kiritimati"), ZoneId.of("Pacific/Kiritimati")))
        assertEquals(at("2026-10-31T00:00:01", "America/St_Johns"), WidgetSchedule.nextRefresh(at("2026-10-30T22:30", "America/St_Johns"), ZoneId.of("America/St_Johns")))
    }

    @Test
    fun `days of 23 and 25 hours`() {
        val oslo = ZoneId.of("Europe/Oslo")
        // 2026-03-29 is 23 hours long in Oslo, 2026-10-25 is 25 hours long.
        assertEquals(Duration.ofHours(23), Duration.between(LocalDate.parse("2026-03-29").atStartOfDay(oslo), LocalDate.parse("2026-03-30").atStartOfDay(oslo)))
        assertEquals(at("2026-03-30T00:00:01", "Europe/Oslo"), WidgetSchedule.nextRefresh(at("2026-03-29T22:00", "Europe/Oslo"), oslo))
        assertEquals(at("2026-10-26T00:00:01", "Europe/Oslo"), WidgetSchedule.nextRefresh(at("2026-10-25T22:00", "Europe/Oslo"), oslo))
        // At 01:30 on the fall-back day the next refresh is three hours on, not at a midnight 22 hours away.
        val early = Instant.parse("2026-10-25T00:30:00Z")
        assertEquals(early.plus(Duration.ofHours(3)), WidgetSchedule.nextRefresh(early, oslo))
        // Lord Howe changes by half an hour.
        val lh = ZoneId.of("Australia/Lord_Howe")
        assertEquals(at("2026-04-06T00:00:01", "Australia/Lord_Howe"), WidgetSchedule.nextRefresh(at("2026-04-05T22:00", "Australia/Lord_Howe"), lh))
    }

    @Test
    fun `a day that starts at 01_00 because its midnight is skipped`() {
        // America/Santiago moves its clocks from 24:00 to 01:00 on 2026-09-06.
        val zone = ZoneId.of("America/Santiago")
        val start = LocalDate.parse("2026-09-06").atStartOfDay(zone)
        assertEquals("the day starts at 01:00", 1, start.hour)
        assertEquals(start.toInstant().plusSeconds(1), WidgetSchedule.nextRefresh(at("2026-09-05T23:00", "America/Santiago"), zone))
    }
}

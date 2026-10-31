package no.stormberry.moonapp.widget

import no.stormberry.moonapp.lunar.LunarDayKind
import no.stormberry.moonapp.lunar.LunarEvent
import no.stormberry.moonapp.lunar.MoonCalc
import no.stormberry.moonapp.lunar.MoonPhase
import no.stormberry.moonapp.lunar.parityRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the widgets show (1.0.8) is what the app's card shows for today in the place's zone: the
 * same percentage, the same times, the same polar case.
 */
class WidgetModelTest {

    private val utc = ZoneId.of("UTC")
    private val oslo = ZoneId.of("Europe/Oslo")
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun `an Oslo place on a UTC phone uses Oslo's day and clock`() {
        // 23:30 UTC on 30 October is already 31 October in Oslo.
        val now = Instant.parse("2026-10-30T23:30:00Z")
        val s = WidgetModel.build(now, "Oslo", 59.9139, 10.7522, "Europe/Oslo", utc)
        assertEquals(LocalDate.parse("2026-10-31"), s.today)
        assertEquals(oslo, s.zone)
        assertTrue(s.zoneDiffers)
        val t = MoonCalc.times(s.today, 59.9139, 10.7522, oslo)
        assertEquals(t[LunarEvent.MOONRISE], s.rise)
        assertEquals(t[LunarEvent.MOONSET], s.set)
        assertEquals(t[LunarEvent.LUNAR_TRANSIT], s.transit)
    }

    @Test
    fun `a Sydney place on an Oslo phone uses Sydney's day, clock and hemisphere`() {
        val now = Instant.parse("2026-10-30T20:00:00Z") // 07:00 on 31 October in Sydney
        val s = WidgetModel.build(now, "Sydney", -33.8688, 151.2093, "Australia/Sydney", oslo)
        assertEquals(LocalDate.parse("2026-10-31"), s.today)
        assertEquals(sydney, s.zone)
        assertTrue(s.zoneDiffers)
        assertEquals("mirrored for the south", s.phase >= 0.5, s.litOnRight)
        val t = MoonCalc.times(s.today, -33.8688, 151.2093, sydney)
        assertEquals(t[LunarEvent.MOONRISE], s.rise)
        assertEquals(t[LunarEvent.MOONSET], s.set)
        assertEquals(MoonPhase.nextPhases(s.today, sydney), s.phases)
    }

    @Test
    fun `a place with no stored zone uses the phone's, and no note`() {
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val s = WidgetModel.build(now, "60.0000, 5.0000", 60.0, 5.0, null, oslo)
        assertEquals(oslo, s.zone)
        assertFalse(s.zoneDiffers)
        val unknown = WidgetModel.build(now, "Somewhere", 60.0, 5.0, "Not/AZone", oslo)
        assertEquals(oslo, unknown.zone)
    }

    @Test
    fun `the percentage and the phase name are the card's for every day of 2026`() {
        var d = LocalDate.parse("2026-01-01")
        while (d.year == 2026) {
            val now = d.atTime(LocalTime.of(9, 0)).atZone(oslo).toInstant()
            val s = WidgetModel.build(now, "Oslo", 59.9139, 10.7522, "Europe/Oslo", oslo)
            val card = MoonCalc.illumination(MoonPhase.noonUtc(d))
            assertEquals("$d", d, s.today)
            assertEquals("$d", Math.round(card.fraction * 100).toInt(), s.percent)
            assertEquals("$d", MoonPhase.name(card.phase), s.phaseName)
            assertEquals("$d", MoonDisc.litOnRight(card.phase, 59.9139), s.litOnRight)
            d = d.plusDays(1)
        }
    }

    @Test
    fun `polar days from the parity corpus give the card's kind, and no transit on a day the moon stays down`() {
        val rows = parityRows("lunar-daykind-parity.csv").filter { it[0] == "Longyearbyen" || it[0] == "Tromso" }
        assertTrue(rows.size > 50)
        val kinds = mutableSetOf<String>()
        for (f in rows) {
            val lat = f[1].toDouble()
            val lon = f[2].toDouble()
            val zone = ZoneId.of(f[3])
            val date = LocalDate.parse(f[4])
            val now = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
            val s = WidgetModel.build(now, f[0], lat, lon, f[3], oslo)
            assertEquals("${f[0]} $date", f[5], s.dayKind.name)
            kinds += f[5]
            val t = MoonCalc.times(date, lat, lon, zone)
            when (s.dayKind) {
                LunarDayKind.ALWAYS_DOWN -> assertNull("${f[0]} $date: transit on an always-down day", s.transit)
                else -> assertEquals("${f[0]} $date", t[LunarEvent.LUNAR_TRANSIT], s.transit)
            }
            if (s.dayKind != LunarDayKind.NORMAL) {
                assertNull(s.rise)
                assertNull(s.set)
            }
        }
        assertEquals("both polar kinds and normal days are covered", setOf("NORMAL", "ALWAYS_UP", "ALWAYS_DOWN"), kinds)
    }
}

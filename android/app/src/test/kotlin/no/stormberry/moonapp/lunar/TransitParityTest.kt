package no.stormberry.moonapp.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The lunar transit, as the website finds it (1.0.4, fixes F-2, F-3, F-5 and M-2).
 *
 * transit-parity.csv is written from the compiled page by tools/gen-lunar-parity.ts: at eight
 * places, every 2026 day without a transit and every day the zone changes its offset, each with
 * the days either side, and every seventh day. A blank transit is a day the moon does not
 * culminate above the 0.133 degree rise threshold inside the place's day, which runs from
 * day_start_iso to day_end_iso (23 or 25 hours on an offset-change day).
 *
 * Regenerate the corpus with: node tools/gen-lunar-parity.ts
 */
class TransitParityTest {

    /** Both sides truncate to whole seconds; a last-bit difference in a sine can move that by one. */
    private val tolerance: Duration = Duration.ofSeconds(1)

    @Test
    fun `the app finds the website's transit, and none where the website has none`() {
        val rows = parityRows("transit-parity.csv")
        assertEquals("rows in transit-parity.csv", 820, rows.size)
        val failures = mutableListOf<String>()
        var blanks = 0
        for (f in rows) {
            val zone = ZoneId.of(f[3])
            val date = LocalDate.parse(f[4])
            assertEquals("${f[0]} ${f[4]}: the website's day starts where the app's does", date.atStartOfDay(zone).toInstant(), Instant.parse(f[5]))
            assertEquals("${f[0]} ${f[4]}: and ends where the app's does", date.plusDays(1).atStartOfDay(zone).toInstant(), Instant.parse(f[6]))
            val got = MoonCalc.times(date, f[1].toDouble(), f[2].toDouble(), zone)[LunarEvent.LUNAR_TRANSIT]
            val want = f.getOrNull(7)?.ifBlank { null }?.let(Instant::parse)
            if (want == null) {
                blanks++
                if (got != null) failures += "${f[0]} ${f[4]}: app $got, website none"
            } else if (got == null) {
                failures += "${f[0]} ${f[4]}: app none, website $want"
            } else if (abs(Duration.between(want, got).toMillis()) > tolerance.toMillis()) {
                failures += "${f[0]} ${f[4]}: app $got, website $want"
            }
        }
        assertTrue("the corpus has days without a transit", blanks > 0)
        val offsetChangeDays = rows.count { Duration.between(Instant.parse(it[5]), Instant.parse(it[6])) != Duration.ofHours(24) }
        assertTrue("the corpus has days a zone changes its offset ($offsetChangeDays)", offsetChangeDays >= 14)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `a culmination near midnight belongs to exactly one day`() {
        // M-2: Sydney, 2026-10-28. Until 1.0.4 the UTC day of the 27th ended on its highest sample
        // and the 28th culminated at 00:33, so a transit alarm rang at 00:00 and again at 00:33.
        val utc = ZoneId.of("UTC")
        val day27 = MoonCalc.times(LocalDate.parse("2026-10-27"), -33.8688, 151.2093, utc)[LunarEvent.LUNAR_TRANSIT]
        val day28 = MoonCalc.times(LocalDate.parse("2026-10-28"), -33.8688, 151.2093, utc)[LunarEvent.LUNAR_TRANSIT]
        val transits = listOfNotNull(day27, day28)
        for (i in 1 until transits.size) {
            assertTrue("two transits $transits under 20 hours apart", Duration.between(transits[i - 1], transits[i]).toHours() >= 20)
        }
        assertNull("the 27th ends before the moon culminates", day27?.takeIf { it >= Instant.parse("2026-10-27T23:45:00Z") })
    }

    @Test
    fun `a culmination at or below the rise threshold is no transit, so no alarm rings for it`() {
        // F-3: the moon's centre peaks between the horizon and 0.133 degrees. The times screen
        // says Always Down; until this fix times() still returned a transit, and
        // OccurrenceEngine rang a LUNAR_TRANSIT alarm for a moon the app said never rose.
        val utc = ZoneId.of("UTC")
        for ((lat, day) in listOf(69.0 to "2026-02-09", -63.0 to "2026-12-23", 77.0 to "2026-05-10")) {
            val date = LocalDate.parse(day)
            assertEquals("$lat $day", LunarDayKind.ALWAYS_DOWN, MoonCalc.dayKind(date, lat, 0.0, utc))
            assertNull("$lat $day", MoonCalc.times(date, lat, 0.0, utc)[LunarEvent.LUNAR_TRANSIT])
        }
    }

    @Test
    fun `on the day the clocks go back the transit in its first hour is that day's`() {
        // F-5: Bergen, 25 October 2026 is 25 hours long. The moon culminates at 00:20:13 CEST. Until
        // this fix the website's day began at 01:00 and showed no transit on the 25th or the 24th.
        val oslo = ZoneId.of("Europe/Oslo")
        val t = MoonCalc.times(LocalDate.parse("2026-10-25"), 60.3913, 5.3221, oslo)[LunarEvent.LUNAR_TRANSIT]
        assertTrue("$t", t != null && abs(Duration.between(Instant.parse("2026-10-24T22:20:13Z"), t).toMillis()) <= tolerance.toMillis())
        assertNull(MoonCalc.times(LocalDate.parse("2026-10-24"), 60.3913, 5.3221, oslo)[LunarEvent.LUNAR_TRANSIT])
        assertEquals(25.0, MoonCalc.dayHours(LocalDate.parse("2026-10-25"), oslo), 0.0)
        assertEquals(23.0, MoonCalc.dayHours(LocalDate.parse("2026-03-29"), oslo), 0.0)
    }

    @Test
    fun `a date the zone skipped has no length, and the screen is told it did not happen`() {
        // Fix round 2: Apia went from UTC-10 to UTC+14 at the end of 29 December 2011, so the 30th
        // starts at the same instant as the 31st (java.time's atStartOfDay), as the website's
        // localDayWindow gives it. The days either side are ordinary 24-hour days.
        val apia = ZoneId.of("Pacific/Apia")
        assertEquals(0.0, MoonCalc.dayHours(LocalDate.parse("2011-12-30"), apia), 0.0)
        assertEquals(24.0, MoonCalc.dayHours(LocalDate.parse("2011-12-29"), apia), 0.0)
        assertEquals(24.0, MoonCalc.dayHours(LocalDate.parse("2011-12-31"), apia), 0.0)
        assertEquals(Instant.parse("2011-12-30T10:00:00Z"), LocalDate.parse("2011-12-30").atStartOfDay(apia).toInstant())
        assertFalse(MoonCalc.dateOccurs(LocalDate.parse("2011-12-30"), apia))
        assertTrue(MoonCalc.dateOccurs(LocalDate.parse("2011-12-29"), apia))
        assertTrue(MoonCalc.dateOccurs(LocalDate.parse("2011-12-31"), apia))
        // Every other day of 2011 to 2027 at Apia, Oslo and Santiago (clocks moved at midnight)
        // occurs, so nothing else ever sees the notice.
        for (zone in listOf(apia, ZoneId.of("Europe/Oslo"), ZoneId.of("America/Santiago"))) {
            var d = LocalDate.parse("2011-01-01")
            while (d.year <= 2027) {
                if (!(zone == apia && d == LocalDate.parse("2011-12-30"))) assertTrue("$zone $d", MoonCalc.dateOccurs(d, zone))
                d = d.plusDays(1)
            }
        }
    }
}

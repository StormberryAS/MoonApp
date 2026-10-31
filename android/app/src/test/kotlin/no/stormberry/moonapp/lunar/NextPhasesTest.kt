package no.stormberry.moonapp.lunar

import no.stormberry.moonapp.lunar.MoonPhase.PrincipalPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The next four principal phases (1.0.8, for the large home screen widget).
 *
 * New and Full Moon must be exactly what the app's card already shows ([MoonPhase.nextLunations],
 * held to the website by LunationParityTest), so the widget's dates are the app's dates. The
 * quarters have no surface of their own yet, so they are held to physics and to the website's own
 * suncalc.js through quarter-parity.csv (tools/gen-quarter-parity.ts).
 */
class NextPhasesTest {

    private val zones = listOf(
        "UTC", "Europe/Oslo", "Arctic/Longyearbyen", "Australia/Sydney", "Pacific/Kiritimati",
        "Pacific/Pago_Pago", "America/St_Johns", "Australia/Lord_Howe",
    ).map { ZoneId.of(it) }

    private fun days(from: String, to: String): Sequence<LocalDate> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(LocalDate.parse(to)) }

    @Test
    fun `new and full moon equal nextLunations for every day of 2026 and 2027 in eight zones`() {
        var checked = 0
        for (zone in zones) for (d in days("2026-01-01", "2027-12-31")) {
            val phases = MoonPhase.nextPhases(d, zone).associateBy { it.phase }
            val lun = MoonPhase.nextLunations(d, zone)
            assertEquals("$zone $d new", lun.nextNew, phases[PrincipalPhase.NEW]?.date)
            assertEquals("$zone $d new", lun.newAt, phases[PrincipalPhase.NEW]?.at)
            assertEquals("$zone $d full", lun.nextFull, phases[PrincipalPhase.FULL]?.date)
            assertEquals("$zone $d full", lun.fullAt, phases[PrincipalPhase.FULL]?.at)
            checked++
        }
        assertEquals(8 * 730, checked)
    }

    @Test
    fun `the four phases are found, in order, a quarter lunation apart, on or after the day`() {
        val cycle = listOf(PrincipalPhase.NEW, PrincipalPhase.FIRST_QUARTER, PrincipalPhase.FULL, PrincipalPhase.LAST_QUARTER)
        for (zone in zones) for (d in days("2026-01-01", "2027-12-31").filterIndexed { i, _ -> i % 3 == 0 }) {
            val p = MoonPhase.nextPhases(d, zone)
            assertEquals("$zone $d: all four", 4, p.size)
            assertEquals("$zone $d: one of each", PrincipalPhase.entries.toSet(), p.map { it.phase }.toSet())
            // Soonest first, and in the order of the cycle from whichever comes first.
            val first = cycle.indexOf(p[0].phase)
            for (i in 0 until 4) assertEquals("$zone $d order", cycle[(first + i) % 4], p[i].phase)
            for (i in 1 until 4) {
                val gap = Duration.between(p[i - 1].at, p[i].at).toMinutes() / 1440.0
                assertTrue("$zone $d: ${p[i - 1].phase} to ${p[i].phase} is $gap days", gap in 6.0..8.5)
            }
            for (e in p) {
                assertEquals("$zone $d: ${e.phase} dated in the zone", e.at.atZone(zone).toLocalDate(), e.date)
                assertTrue("$zone $d: ${e.phase} on ${e.date} is before the day", !e.date.isBefore(d))
                assertTrue("$zone $d: ${e.phase} on ${e.date} is too late", !e.date.isAfter(d.plusDays(30)))
                val k = MoonCalc.illumination(e.at).fraction
                when (e.phase) {
                    PrincipalPhase.NEW -> assertTrue("new moon lit $k", k < 0.01)
                    PrincipalPhase.FULL -> assertTrue("full moon lit $k", k > 0.99)
                    else -> assertTrue("${e.phase} lit $k", abs(k - 0.5) <= 0.03)
                }
            }
        }
    }

    /** The bisection runs on both sides to 0.02 ms; the sine of a JVM and of V8 may differ in the last bit. */
    private val tolerance: Duration = Duration.ofSeconds(2)

    @Test
    fun `the quarters equal the website's suncalc for every row of quarter-parity_csv`() {
        val rows = parityRows("quarter-parity.csv")
        assertTrue("rows in quarter-parity.csv: ${rows.size}", rows.size > 400)
        val failures = mutableListOf<String>()
        for (f in rows) {
            val zone = ZoneId.of(f[0])
            val date = LocalDate.parse(f[1])
            val got = MoonPhase.nextPhases(date, zone).associateBy { it.phase }
            for ((phase, wantDate, wantAt) in listOf(
                Triple(PrincipalPhase.FIRST_QUARTER, f[2], f[3]),
                Triple(PrincipalPhase.LAST_QUARTER, f[4], f[5]),
            )) {
                val e = got[phase]
                if (e == null) { failures += "${f[0]} ${f[1]} $phase: app none, website $wantAt"; continue }
                val delta = abs(Duration.between(Instant.parse(wantAt), e.at).toMillis())
                if (delta > tolerance.toMillis()) failures += "${f[0]} ${f[1]} $phase: app ${e.at}, website $wantAt"
                if (e.date.toString() != wantDate) failures += "${f[0]} ${f[1]} $phase: app shows ${e.date}, website $wantDate"
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `a quarter early on the chosen day in Kiritimati is that day's`() {
        // The scan starts two days early for zones far ahead of UTC; a quarter that falls in the
        // first hours of the day there must still be today's, not the next lunation's.
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        for (d in days("2026-01-01", "2026-12-31")) {
            for (e in MoonPhase.nextPhases(d, kiritimati)) {
                val start = d.atStartOfDay(kiritimati).toInstant()
                if (e.at.isBefore(start)) error("$d: ${e.phase} at ${e.at} is before the day")
            }
            // Any quarter that falls inside this day must be the one returned for it.
            val prev = MoonPhase.nextPhases(d.minusDays(1), kiritimati)
            for (e in prev.filter { it.date == d }) {
                assertEquals("$d ${e.phase}", e, MoonPhase.nextPhases(d, kiritimati).single { it.phase == e.phase })
            }
        }
    }
}

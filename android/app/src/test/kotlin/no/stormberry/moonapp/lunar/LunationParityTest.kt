package no.stormberry.moonapp.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The next new and full moon, as the website gives them (1.0.4, fix F-1).
 *
 * lunation-parity.csv is written from the compiled page by tools/gen-lunar-parity.ts: for eight
 * zones, every 2026 lunation's own day and the day after, the zones' offset changes, both ends of
 * the date picker's range, and two days whose event falls in the first two hours of the day in
 * Kiritimati (UTC+14). Until 1.0.4 both surfaces sampled at 12:00 UTC once a day and showed a UTC
 * date, a day late about half the time, and skipped an event earlier on the chosen day.
 *
 * Regenerate the corpus with: node tools/gen-lunar-parity.ts
 */
class LunationParityTest {

    /** The bisection runs on both sides to 0.02 ms; the sine of a JVM and of V8 may differ in the last bit. */
    private val tolerance: Duration = Duration.ofSeconds(2)

    @Test
    fun `the app gives the website's next new and full moon for every row`() {
        val rows = parityRows("lunation-parity.csv")
        assertEquals("rows in lunation-parity.csv", 487, rows.size)
        val failures = mutableListOf<String>()
        var worst = 0L
        for (f in rows) {
            val zone = ZoneId.of(f[0])
            val date = LocalDate.parse(f[1])
            val got = MoonPhase.nextLunations(date, zone)
            for ((what, wantDate, wantAt, gotDate, gotAt) in listOf(
                Five("new", f[2], f[3], got.nextNew, got.newAt),
                Five("full", f[4], f[5], got.nextFull, got.fullAt),
            )) {
                if (wantAt.isEmpty()) {
                    if (gotAt != null) failures += "${f[0]} ${f[1]} $what: app $gotAt, website none"
                    continue
                }
                if (gotAt == null) { failures += "${f[0]} ${f[1]} $what: app none, website $wantAt"; continue }
                val delta = abs(Duration.between(Instant.parse(wantAt), gotAt).toMillis())
                worst = maxOf(worst, delta)
                if (delta > tolerance.toMillis()) failures += "${f[0]} ${f[1]} $what: app $gotAt, website $wantAt"
                if (gotDate.toString() != wantDate) failures += "${f[0]} ${f[1]} $what: app shows $gotDate, website $wantDate"
            }
        }
        assertTrue("worst ${worst} ms; ${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `an event early on the chosen day is that day's, not the next lunation`() {
        // The two cases fix F-1 is about, pinned by name so the corpus cannot lose them unseen.
        // Longyearbyen, 3 January 2026: the full moon at 07:13 UTC came before the old first
        // sample (12:00 UTC) and the card showed 2 February.
        val svalbard = MoonPhase.nextLunations(LocalDate.parse("2026-01-03"), ZoneId.of("Arctic/Longyearbyen"))
        assertEquals(LocalDate.parse("2026-01-03"), svalbard.nextFull)
        // Kiritimati, 19 April 2000: the full moon at 10:48 UTC on the 18th is 00:48 on the 19th there.
        val kiritimati = MoonPhase.nextLunations(LocalDate.parse("2000-04-19"), ZoneId.of("Pacific/Kiritimati"))
        assertEquals(LocalDate.parse("2000-04-19"), kiritimati.nextFull)
        // A full moon later on the chosen day is that day's (decision D-4): 1 February 2026, 17:33 UTC.
        assertEquals(
            LocalDate.parse("2026-02-01"),
            MoonPhase.nextLunations(LocalDate.parse("2026-02-01"), ZoneId.of("Europe/Oslo")).nextFull,
        )
    }

    private data class Five(val a: String, val b: String, val c: String, val d: LocalDate?, val e: Instant?)
}

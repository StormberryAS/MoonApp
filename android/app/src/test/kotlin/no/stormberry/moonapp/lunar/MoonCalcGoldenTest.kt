package no.stormberry.moonapp.lunar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Golden-vector parity between the Kotlin lunar maths and SunCalc 1.8.0, which is the
 * library the web app at moon.stormberry.as actually ships. The two must agree, because a
 * visitor who reads one number on the website and a different one in the app reports that
 * as a bug, and rightly so.
 *
 * WHY THIS TEST EXISTS. Until 2026-09-04, MoonCalc.illumination computed the Sun's position
 * with 360.9856235 degrees per day, the coefficient from SunCalc's siderealTime(), instead
 * of the Sun's ecliptic longitude at 0.98560028. The computed sun swept a full circle every
 * 24 hours, so the phase completed an entire lunation per day: a 2 percent crescent was
 * reported as 97 percent full at noon. It was correct at exactly midnight UTC, because
 * 360.9856235 mod 360 is very nearly 0.9856235, which is why a daily spot-check passed and
 * the bug survived to a store submission audit.
 *
 * The vectors below therefore sample five times of day across all twelve months. A test that
 * only checked midnight would still pass against the broken formula.
 *
 * Regenerate with: node tools/gen-golden.mjs
 */
class MoonCalcGoldenTest {

    private data class Vector(val at: Instant, val fraction: Double, val phase: Double)

    private fun vectors(): List<Vector> =
        javaClass.classLoader!!.getResourceAsStream("mooncalc-golden.csv")!!
            .bufferedReader().useLines { lines ->
                lines.filterNot { it.isBlank() || it.startsWith("#") }
                    .map { line ->
                        val f = line.split(",")
                        Vector(Instant.parse(f[0]), f[1].toDouble(), f[2].toDouble())
                    }.toList()
            }

    @Test
    fun `illuminated fraction matches SunCalc at every sampled hour`() {
        val vs = vectors()
        assertTrue("golden file did not load", vs.size >= 60)
        var worst = 0.0
        var worstAt = vs.first().at
        for (v in vs) {
            val got = MoonCalc.illumination(v.at).fraction
            val delta = kotlin.math.abs(got - v.fraction)
            if (delta > worst) { worst = delta; worstAt = v.at }
        }
        assertTrue(
            "fraction diverges from SunCalc by $worst at $worstAt (tolerance 1e-6)",
            worst < 1e-6
        )
    }

    @Test
    fun `phase matches SunCalc at every sampled hour`() {
        var worst = 0.0
        var worstAt = Instant.EPOCH
        for (v in vectors()) {
            val got = MoonCalc.illumination(v.at).phase
            // phase is cyclic in [0,1): 0.999 and 0.001 are two thousandths apart, not 0.998
            val raw = kotlin.math.abs(got - v.phase)
            val delta = minOf(raw, 1.0 - raw)
            if (delta > worst) { worst = delta; worstAt = v.at }
        }
        assertTrue("phase diverges from SunCalc by $worst at $worstAt (tolerance 1e-6)", worst < 1e-6)
    }

    /**
     * The specific shape of the 2026-09-04 bug: within a single day the illuminated fraction
     * must change by a small amount, not sweep an entire lunation.
     *
     * The bound is physical, not empirical. fraction = (1 - cos t)/2 with t advancing 2*pi per
     * synodic month, so d(fraction)/dt peaks at quarter phase at pi/29.53 = 0.1064 per day.
     * The threshold below sits 41 percent above that ceiling, and 85 percent below the 0.998
     * the broken formula produced on this very date. It cannot fire on correct maths and
     * cannot miss a repeat of the bug.
     */
    @Test
    fun `illumination does not complete a cycle within one day`() {
        val start = Instant.parse("2026-09-04T00:00:00Z")
        var lo = 1.0
        var hi = 0.0
        for (h in 0..23) {
            val f = MoonCalc.illumination(start.plusSeconds(h * 3600L)).fraction
            if (f < lo) lo = f
            if (f > hi) hi = f
        }
        assertTrue("illumination swung $lo..$hi in 24 hours; physical maximum is 0.107", hi - lo < 0.15)
    }
}

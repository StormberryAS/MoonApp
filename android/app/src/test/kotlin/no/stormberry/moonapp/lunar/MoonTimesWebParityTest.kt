package no.stormberry.moonapp.lunar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Parity with the WEBSITE, which is not the same claim as [MoonTimesGoldenTest].
 *
 * That test pins MoonCalc against suncalc.js in UTC mode, and it passed for the whole of
 * v1.0.0 while the app still disagreed with moon.stormberry.as, because the website does not
 * use a UTC day. app.js:327 anchors its search on the CITY'S local midnight expressed as an
 * instant:
 *
 *     const tzOffsetMs      = cityUtcOffsetMs(tz, dateForCalc);
 *     const cityMidnightUtc = new Date(Date.UTC(year, month - 1, day) - tzOffsetMs);
 *     const moonTimes       = SunCalc.getMoonTimes(cityMidnightUtc, lat, lon, true);
 *
 * `MoonCalc.times` took no zone at all and always started at UTC midnight, so for anywhere
 * with a non-zero offset the two surfaces searched different 24-hour windows and answered
 * differently for the same calendar date: over an hour apart at Bergen, and a full calendar
 * day apart on moonset at Sydney. The website side of this was fixed on 2026-09-04 and the
 * Android side was not, which is precisely the gap a suncalc-only corpus cannot see.
 *
 * Regenerate the corpus with: node tools/gen-webparity-golden.mjs
 */
class MoonTimesWebParityTest {

    private data class Row(
        val place: String, val lat: Double, val lon: Double, val zone: ZoneId,
        val date: LocalDate, val rise: Instant?, val set: Instant?, val flag: String,
    )

    private fun rows(): List<Row> =
        javaClass.classLoader!!.getResourceAsStream("webparity-golden.csv")!!
            .bufferedReader().useLines { lines ->
                lines.filterNot { it.isBlank() || it.startsWith("#") }.map { l ->
                    val f = l.split(",")
                    Row(
                        f[0], f[1].toDouble(), f[2].toDouble(), ZoneId.of(f[3]),
                        LocalDate.parse(f[4]),
                        f[5].ifBlank { null }?.let(Instant::parse),
                        f[6].ifBlank { null }?.let(Instant::parse),
                        if (f.size > 7) f[7] else "",
                    )
                }.toList()
            }

    /** Same two minutes as the sibling test, and for the same reason. */
    private val tolerance: Duration = Duration.ofMinutes(2)

    @Test
    fun `the app agrees with the website when the day window is the place's own`() {
        var checked = 0
        var worst = 0L
        val failures = mutableListOf<String>()
        for (r in rows()) {
            if (r.flag.isNotEmpty()) continue
            val got = MoonCalc.times(r.date, r.lat, r.lon, r.zone)
            for ((label, want) in listOf("rise" to r.rise, "set" to r.set)) {
                if (want == null) continue
                val actual =
                    if (label == "rise") got[LunarEvent.MOONRISE] else got[LunarEvent.MOONSET]
                if (actual == null) {
                    failures += "${r.place} ${r.date} $label: app returned nothing, website said $want"
                    continue
                }
                val delta = abs(Duration.between(want, actual).toMillis())
                if (delta > worst) worst = delta
                if (delta > tolerance.toMillis()) {
                    failures += "${r.place} ${r.date} $label: app $actual, website $want, " +
                        "off by ${delta / 1000}s"
                }
                checked++
            }
        }
        assertTrue("no vectors were checked", checked > 0)
        assertTrue(
            "worst disagreement ${worst / 1000}s over $checked vectors:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /**
     * The regression guard proper: with a UTC day window the corpus must FAIL for places
     * whose offset is not zero. Without this, someone could delete the zone parameter, the
     * test above would keep passing on Reykjavik and the Equator alone, and the bug would be
     * back with a green build.
     */
    @Test
    fun `a UTC day window disagrees with the website, which is why the zone parameter exists`() {
        val offenders = rows()
            .filter { it.flag.isEmpty() && it.rise != null }
            .filter { it.zone.rules.getOffset(Instant.parse("${it.date}T12:00:00Z")).totalSeconds != 0 }
            .count { r ->
                val utc = MoonCalc.times(r.date, r.lat, r.lon)[LunarEvent.MOONRISE]
                utc == null || abs(Duration.between(r.rise, utc).toMillis()) > tolerance.toMillis()
            }
        assertTrue(
            "a UTC day window matched the website everywhere, so the zone parameter is doing " +
                "nothing and this suite is not testing what it claims",
            offenders > 0,
        )
    }
}

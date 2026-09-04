package no.stormberry.moonapp.lunar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Moonrise and moonset parity with suncalc.js, the library moon.stormberry.as runs.
 *
 * WHY THIS EXISTS. Until 2026-09-04 MoonCalc.times scanned the day in 20-minute jumps and
 * took the first step past the horizon as the answer, with no interpolation and no horizon
 * correction at all. Rise and set were therefore quantised to a 20-minute grid and offset by
 * the moon's apparent radius plus refraction, so the app and the website disagreed by up to
 * half an hour on the same date and city. The implementation now mirrors SunCalc's hourly
 * search with quadratic interpolation and its 0.133 rad horizon offset.
 *
 * Regenerate the corpus with: node tools/gen-moontimes-golden.mjs
 */
class MoonTimesGoldenTest {

    private data class Row(
        val place: String, val lat: Double, val lon: Double, val date: LocalDate,
        val rise: Instant?, val set: Instant?, val flag: String
    )

    private fun rows(): List<Row> =
        javaClass.classLoader!!.getResourceAsStream("moontimes-golden.csv")!!
            .bufferedReader().useLines { lines ->
                lines.filterNot { it.isBlank() || it.startsWith("#") }.map { l ->
                    val f = l.split(",")
                    Row(f[0], f[1].toDouble(), f[2].toDouble(), LocalDate.parse(f[3]),
                        f[4].ifBlank { null }?.let(Instant::parse),
                        f[5].ifBlank { null }?.let(Instant::parse),
                        if (f.size > 6) f[6] else "")
                }.toList()
            }

    /**
     * Two minutes, not two seconds. SunCalc and this port share the same lunar position model
     * but interpolate over slightly different sample grids, so exact equality is not the goal:
     * agreeing to within a couple of minutes is, because that is the resolution a person reads
     * off a screen. The old implementation missed by up to thirty.
     */
    private val tolerance: Duration = Duration.ofMinutes(2)

    @Test
    fun `moonrise and moonset agree with the website within two minutes`() {
        var checked = 0
        var worst = 0L
        val failures = mutableListOf<String>()
        for (r in rows()) {
            if (r.flag.isNotEmpty()) continue          // polar cases are asserted separately
            val got = MoonCalc.times(r.date, r.lat, r.lon)
            for ((label, want) in listOf("rise" to r.rise, "set" to r.set)) {
                if (want == null) continue
                val actual = if (label == "rise") got[LunarEvent.MOONRISE] else got[LunarEvent.MOONSET]
                if (actual == null) { failures += "${r.place} ${r.date} $label: app returned nothing, website said $want"; continue }
                val off = abs(Duration.between(want, actual).toMillis())
                if (off > worst) worst = off
                if (off > tolerance.toMillis())
                    failures += "${r.place} ${r.date} $label: off by ${off / 60000.0} min (want $want, got $actual)"
                checked++
            }
        }
        assertTrue("corpus did not load", checked > 20)
        assertTrue(
            "worst disagreement ${worst / 60000.0} min over $checked comparisons:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * At high latitude the moon can stay up or stay down for a whole day. The website reports
     * that with alwaysUp or alwaysDown and no times. The app must not invent a time for those
     * days, because an alarm anchored to a moonrise that never happens has to be skipped, not
     * fired at a guess.
     */
    @Test
    fun `days with no rise or set report nothing rather than a guess`() {
        val polar = rows().filter { it.flag.isNotEmpty() }
        assertTrue("corpus contains no polar cases to check", polar.isNotEmpty())
        for (r in polar) {
            val got = MoonCalc.times(r.date, r.lat, r.lon)
            val invented = listOfNotNull(
                got[LunarEvent.MOONRISE]?.let { "rise=$it" },
                got[LunarEvent.MOONSET]?.let { "set=$it" }
            )
            assertTrue(
                "${r.place} ${r.date} is ${r.flag} on the website but the app returned $invented",
                invented.isEmpty()
            )
        }
    }

    /**
     * TRANSIT. It used to be read off the rise/set loop, which steps two hours at a time and
     * breaks as soon as both crossings are found, so on most days the scan never reached the
     * peak: median error 45 minutes over 528 comparisons, worst case 23 hours at Tromso, and a
     * LUNAR_TRANSIT alarm rang at the wrong time. Verify against a brute-force one-minute scan,
     * which is the ground truth rather than either implementation.
     */
    @Test
    fun `lunar transit lands on the true altitude peak`() {
        val cases = listOf(
            Triple("Bergen", 60.3913, 5.3221) to LocalDate.of(2026, 9, 7),
            Triple("Tromso", 69.6492, 18.9553) to LocalDate.of(2026, 9, 29),
            Triple("Sydney", -33.8688, 151.2093) to LocalDate.of(2026, 9, 9),
            Triple("Rio", -22.9068, -43.1729) to LocalDate.of(2026, 8, 24),
        )
        for ((place, date) in cases) {
            val (name, lat, lon) = place
            val got = MoonCalc.times(date, lat, lon)[LunarEvent.LUNAR_TRANSIT]
                ?: error("$name $date produced no transit")
            // brute force: sample every minute of the UTC day and take the true maximum
            val start = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
            var bestAt = start
            var best = -Double.MAX_VALUE
            for (m in 0..1440) {
                val i = start.plusSeconds(m * 60L)
                val a = MoonCalc.position(i, lat, lon).altitudeRad
                if (a > best) { best = a; bestAt = i }
            }
            val offMinutes = abs(Duration.between(bestAt, got).toMinutes())
            assertTrue(
                "$name $date transit off by $offMinutes min (true peak $bestAt, got $got)",
                offMinutes <= 10
            )
        }
    }

    /**
     * A day with neither a rise nor a set is either "up all day" or "down all day", and those
     * are opposite facts. The UI used to render both as "does not occur today".
     */
    @Test
    fun `polar days are classified up or down, not merely absent`() {
        var sawUp = false
        var sawDown = false
        var d = LocalDate.of(2026, 1, 1)
        repeat(120) {
            when (MoonCalc.dayKind(d, 78.22, 15.65)) {   // Longyearbyen
                LunarDayKind.ALWAYS_UP -> sawUp = true
                LunarDayKind.ALWAYS_DOWN -> sawDown = true
                LunarDayKind.NORMAL -> {}
            }
            d = d.plusDays(1)
        }
        assertTrue("no always-up day found at Longyearbyen in 120 days", sawUp)
        assertTrue("no always-down day found at Longyearbyen in 120 days", sawDown)
    }
}

package no.stormberry.moonapp.lunar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Phase naming and the next two lunations, ported from the web app's app.js so the APK and
 * moon.stormberry.as answer identically.
 *
 * The web samples EVERY figure on the card at 12:00 UTC of the selected date:
 *
 *     const dateForCalc = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));   // app.js:315
 *
 * The Android side used `Instant.now()` instead, which made illumination drift through the day
 * and disagree with the website by a point on roughly half of all days. [noonUtc] is the single
 * place that decision lives now; call it rather than Instant.now() for anything shown on the
 * times card.
 */
object MoonPhase {

    /** 12:00 UTC on [date]. The instant every figure on the times card is sampled at. */
    fun noonUtc(date: LocalDate): Instant = date.atTime(12, 0).toInstant(ZoneOffset.UTC)

    /**
     * The eight phase names, with the same band edges as the website's getMoonPhaseName.
     *
     * `phase` runs 0 to 1: 0 = New Moon, 0.25 = First Quarter, 0.5 = Full Moon,
     * 0.75 = Last Quarter. The first band wraps, which is why it tests both ends.
     */
    fun name(phase: Double): String = when {
        phase < 0.0625 || phase >= 0.9375 -> "New Moon"
        phase < 0.1875 -> "Waxing Crescent"
        phase < 0.3125 -> "First Quarter"
        phase < 0.4375 -> "Waxing Gibbous"
        phase < 0.5625 -> "Full Moon"
        phase < 0.6875 -> "Waning Gibbous"
        phase < 0.8125 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    /**
     * The next New Moon and Full Moon: [nextNew] and [nextFull] are the dates they fall on in
     * the place's zone, [newAt] and [fullAt] the moments themselves; null if none is found.
     */
    data class Lunations(
        val nextNew: LocalDate?,
        val nextFull: LocalDate?,
        val newAt: Instant? = null,
        val fullAt: Instant? = null,
    )

    /** Daily samples [nextLunations] takes, from two days before [from]: enough for both events. */
    private const val SEARCH_DAYS = 38

    private const val DAY_MS = 86_400_000L

    /**
     * The first New Moon and the first Full Moon at or after the start of [from] in [zone],
     * dated in that zone (1.0.4, fix F-1; decision D-4). The same algorithm as the website's
     * getNextLunations, held to it by lunation-parity.csv.
     *
     * Until 1.0.4 this sampled the phase at 12:00 UTC once a day and returned the first noon
     * past the crossing, as a UTC date. A lunation between noon and midnight UTC showed a day
     * late, about half the time, and one between midnight and noon UTC of the chosen day came
     * before the first sample and was skipped, so the card showed the one a lunation later. Now
     * the daily samples only bracket each crossing, [crossing] finds the moment, and the date is
     * the place's. New Moon is where the phase wraps past 1 back to 0, Full Moon where it crosses
     * 0.5 upward. The samples start two days early, at 12:00 UTC, because a zone up to 14 hours
     * ahead of UTC begins the chosen day at 10:00 UTC the day before; an event that falls before
     * the chosen day is skipped and the search goes on to the next.
     */
    fun nextLunations(from: LocalDate, zone: ZoneId = ZoneOffset.UTC): Lunations {
        val start = noonUtc(from).toEpochMilli() - 2 * DAY_MS
        var newAt: Long? = null
        var fullAt: Long? = null
        var prevPhase = phaseAt(start.toDouble())
        for (d in 1..SEARCH_DAYS) {
            val t1 = start + d * DAY_MS
            val currPhase = phaseAt(t1.toDouble())
            if (newAt == null && prevPhase > 0.75 && currPhase < 0.25) {
                val at = crossing(t1 - DAY_MS, t1, 1.0)
                if (!localDate(at, zone).isBefore(from)) newAt = at
            }
            if (fullAt == null && prevPhase < 0.5 && currPhase >= 0.5) {
                val at = crossing(t1 - DAY_MS, t1, 0.5)
                if (!localDate(at, zone).isBefore(from)) fullAt = at
            }
            if (newAt != null && fullAt != null) break
            prevPhase = currPhase
        }
        return Lunations(
            nextNew = newAt?.let { localDate(it, zone) },
            nextFull = fullAt?.let { localDate(it, zone) },
            newAt = newAt?.let { Instant.ofEpochMilli(it) },
            fullAt = fullAt?.let { Instant.ofEpochMilli(it) },
        )
    }

    /**
     * The moment (epoch milliseconds) the phase reaches [target] inside [t0, t1], by bisection:
     * 0.5 for a Full Moon, 1 for a New Moon (where the phase wraps, so a phase below 0.5 counts
     * as past 1). Thirty-two halvings of a day leave 0.02 ms. The website's phaseCrossing, step
     * for step: the midpoints stay fractional and each sample is taken at the whole millisecond
     * below, as a JavaScript Date truncates.
     */
    internal fun crossing(t0: Long, t1: Long, target: Double): Long {
        fun f(t: Double): Double {
            var p = phaseAt(t)
            if (target == 1.0 && p < 0.5) p += 1.0
            return p - target
        }
        var a = t0.toDouble()
        var b = t1.toDouble()
        repeat(32) {
            val m = (a + b) / 2
            if (f(a) * f(m) <= 0) b = m else a = m
        }
        return Math.round((a + b) / 2)
    }

    /** The phase at [t] epoch milliseconds, truncated toward zero as `new Date(t)` does. */
    private fun phaseAt(t: Double): Double = MoonCalc.illumination(Instant.ofEpochMilli(t.toLong())).phase

    private fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * "59.9139°, 10.7522°", the web's format at app.js:369.
     *
     * Deliberately no N/E/S/W letters. The old card hardcoded "N" and "E", so every southern
     * or western place, roughly half the 25,007-city catalogue, was labelled with the wrong
     * hemisphere next to a contradictory minus sign.
     */
    fun formatCoordinates(lat: Double, lon: Double): String =
        "%.4f°, %.4f°".format(lat, lon)
}

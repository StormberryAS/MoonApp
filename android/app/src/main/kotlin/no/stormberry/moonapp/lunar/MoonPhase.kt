package no.stormberry.moonapp.lunar

import java.time.Instant
import java.time.LocalDate
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
     * The eight phase names, with the same band edges as app.js:455-462.
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

    /** The next New Moon and Full Moon after [from], or null if none inside 35 days. */
    data class Lunations(val nextNew: LocalDate?, val nextFull: LocalDate?)

    /**
     * Day-by-day crossing search, the same algorithm as app.js:469-483.
     *
     * New Moon is detected where the phase wraps past 1 back to 0, Full Moon where it crosses
     * 0.5 upward. A day is coarse, but it matches the web exactly and the card only ever
     * prints a date.
     */
    fun nextLunations(from: LocalDate): Lunations {
        var nextNew: LocalDate? = null
        var nextFull: LocalDate? = null
        var prevPhase = MoonCalc.illumination(noonUtc(from)).phase

        for (d in 1..35) {
            val candidate = from.plusDays(d.toLong())
            val currPhase = MoonCalc.illumination(noonUtc(candidate)).phase
            if (nextNew == null && prevPhase > 0.75 && currPhase < 0.25) nextNew = candidate
            if (nextFull == null && prevPhase < 0.5 && currPhase >= 0.5) nextFull = candidate
            if (nextNew != null && nextFull != null) break
            prevPhase = currPhase
        }
        return Lunations(nextNew, nextFull)
    }

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

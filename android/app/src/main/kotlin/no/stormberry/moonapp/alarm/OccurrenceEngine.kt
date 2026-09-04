package no.stormberry.moonapp.alarm

import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.MoonCalc
import java.time.Instant
import java.time.LocalDate

/**
 * Occurrence engine responsible for computing the exact future trigger timestamp for a lunar alarm rule.
 *
 * For entry-level programmers:
 * Because the Moon rises at a different time every day (roughly 50 minutes later each day), an astronomical
 * alarm cannot simply repeat at a fixed clock time like 07:00 AM.
 * The OccurrenceEngine looks at calendar dates starting from today, calculates the precise astronomical
 * event time for the rule's location, adds any user-defined offset minutes, and returns the next future
 * timestamp when the system alarm should ring.
 */
object OccurrenceEngine {

    /**
     * Finds the next future firing timestamp for the given [rule] starting after [now].
     *
     * @param rule The user's configured lunar alarm rule.
     * @param now The current timestamp (defaults to Instant.now()).
     * @return The next Instant when the alarm should fire, or null if no event occurs within 30 days.
     */
    fun findNextOccurrence(rule: MoonAlarmRule, now: Instant = Instant.now()): Instant? {
        if (!rule.enabled) return null

        // Derive the search start from the `now` argument rather than the wall clock. The
        // parameter was accepted and then ignored, so the function could not be tested and any
        // caller passing a time got answers for today instead. MoonCalc.times works in UTC
        // days, so the date is taken in UTC to match. Found by OccurrenceEngineTest 2026-09-04.
        val currentDate = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        // Search up to 30 days into the future to find the next upcoming event instance.
        for (dayOffset in 0..30) {
            val dateToCheck = currentDate.plusDays(dayOffset.toLong())
            val eventTimes = MoonCalc.times(dateToCheck, rule.latDeg, rule.lonDeg)
            val baseInstant = eventTimes[rule.event]

            if (baseInstant != null) {
                // Apply the user's offset (e.g. -30 minutes before Moonrise)
                val targetInstant = baseInstant.plusSeconds(rule.offsetMinutes * 60L)

                // Ensure the calculated target time is in the future
                if (targetInstant.isAfter(now)) {
                    return targetInstant
                }
            }
        }
        return null
    }
}

package no.stormberry.moonapp.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which day is "today" for the chosen place (1.0.8, Marcos 2026-10-30: the app opens on the
 * city's today, not the phone's). Pure, so PlaceDayTest runs it on the JVM.
 *
 * The times screen and the home screen widgets both start from here, so the two always agree:
 * with Sydney chosen on a phone in Oslo late in the evening, it is already tomorrow in Sydney,
 * and the app opens on that date, as the widget shows it. Before 1.0.8 the screen started at
 * `LocalDate.now()`, the phone's date, one day behind the widget for those hours.
 */
object PlaceDay {

    /**
     * The place's zone: the stored IANA id, or the phone's zone when there is none (manual
     * coordinates have no zone) or it is not one this device knows.
     */
    fun zoneOf(tz: String?, deviceZone: ZoneId): ZoneId =
        runCatching { ZoneId.of(tz ?: "") }.getOrElse { deviceZone }

    /** Today's date in [zone] at [now]. */
    fun today(zone: ZoneId, now: Instant = Instant.now()): LocalDate = now.atZone(zone).toLocalDate()

    /**
     * The date to show after the place changes from [oldZone] to [newZone]. A screen that showed
     * today for the old place moves to today for the new one, so it keeps agreeing with the
     * widget; a date picked by hand stays as it is.
     */
    fun afterPlaceChange(shown: LocalDate, oldZone: ZoneId, newZone: ZoneId, now: Instant = Instant.now()): LocalDate =
        if (shown == today(oldZone, now)) today(newZone, now) else shown
}

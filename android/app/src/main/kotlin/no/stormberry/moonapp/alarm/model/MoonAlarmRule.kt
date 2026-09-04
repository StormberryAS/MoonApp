package no.stormberry.moonapp.alarm.model

import no.stormberry.moonapp.lunar.LunarEvent

/**
 * Data class defining a user's Lunar Alarm rule in MoonApp.
 *
 * For entry-level programmers:
 * A data class in Kotlin holds state without boilerplate getters/setters.
 * This class represents a single alarm configured by the user, such as:
 * "Wake me 30 minutes before Moonrise in Oslo".
 *
 * @param id Unique identifier string for the rule.
 * @param label Descriptive title for the alarm shown on screen.
 * @param event The target lunar astronomical event (e.g., Moonrise, Full Moon).
 * @param offsetMinutes Minutes offset relative to the event (-30 = 30 mins before, 0 = exact time).
 * @param cityName Name of the location selected for lunar calculations.
 * @param latDeg Latitude of the location in degrees.
 * @param lonDeg Longitude of the location in degrees.
 * @param enabled Whether the alarm is currently active or toggled off.
 * @param vibrate Whether to vibrate when the alarm fires.
 */
data class MoonAlarmRule(
    val id: String,
    val label: String,
    val event: LunarEvent,
    val offsetMinutes: Int = 0,
    val cityName: String = "Oslo",
    val latDeg: Double = 59.9139,
    val lonDeg: Double = 10.7522,
    val enabled: Boolean = true,
    val vibrate: Boolean = true
)

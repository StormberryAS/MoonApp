package no.stormberry.moonapp.ui

import no.stormberry.moonapp.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The times screen opens on the place's today, not the phone's (1.0.8, Marcos 2026-10-30), and
 * that is the day the home screen widgets show. Also the card's placeholder for a missing time.
 */
class PlaceDayTest {

    private val oslo = ZoneId.of("Europe/Oslo")
    private val sydney = ZoneId.of("Australia/Sydney")

    /** 23:30 in Oslo on Friday 30 October 2026 (CET, UTC+1) is 09:30 on Saturday in Sydney. */
    private val osloLateEvening = Instant.parse("2026-10-30T22:30:00Z")

    @Test fun `the start date follows the place's zone, not the phone's`() {
        assertEquals(LocalDate.parse("2026-10-31"), PlaceDay.today(sydney, osloLateEvening))
        assertEquals(LocalDate.parse("2026-10-30"), PlaceDay.today(oslo, osloLateEvening))
        // The phone's zone plays no part once the place has one.
        assertEquals(sydney, PlaceDay.zoneOf("Australia/Sydney", oslo))
        assertEquals(
            LocalDate.parse("2026-10-31"),
            PlaceDay.today(PlaceDay.zoneOf("Australia/Sydney", oslo), osloLateEvening),
        )
    }

    @Test fun `a place without a known zone falls back to the phone's`() {
        assertEquals(oslo, PlaceDay.zoneOf(null, oslo))
        assertEquals(oslo, PlaceDay.zoneOf("", oslo))
        assertEquals(oslo, PlaceDay.zoneOf("Not/AZone", oslo))
    }

    /**
     * The app's start date and the widget's day are the same date, every hour across a day
     * boundary, for places east and west of the phone and across a DST change.
     */
    @Test fun `the app opens on the day the widget shows`() {
        val places = listOf("Australia/Sydney", "Pacific/Kiritimati", "Pacific/Pago_Pago", "America/St_Johns", "Europe/Oslo", null)
        val phones = listOf(oslo, ZoneId.of("UTC"), ZoneId.of("America/Los_Angeles"))
        var at = Instant.parse("2026-10-24T00:00:00Z")
        val end = Instant.parse("2026-10-27T00:00:00Z")
        while (at < end) {
            for (tz in places) for (phone in phones) {
                val app = PlaceDay.today(PlaceDay.zoneOf(tz, phone), at)
                val widget = WidgetModel.build(at, "Place", 59.91, 10.75, tz, phone).today
                assertEquals("$tz on a $phone phone at $at", widget, app)
            }
            at = at.plus(Duration.ofMinutes(61))
        }
    }

    @Test fun `a new place moves today to its today, and keeps a date picked by hand`() {
        val osloToday = LocalDate.parse("2026-10-30")
        assertEquals(LocalDate.parse("2026-10-31"), PlaceDay.afterPlaceChange(osloToday, oslo, sydney, osloLateEvening))
        val picked = LocalDate.parse("2026-12-24")
        assertEquals(picked, PlaceDay.afterPlaceChange(picked, oslo, sydney, osloLateEvening))
        // Same zone: nothing moves.
        assertEquals(osloToday, PlaceDay.afterPlaceChange(osloToday, oslo, oslo, osloLateEvening))
    }

    @Test fun `a missing time on the card is an en dash, as on the widget`() {
        assertEquals("\u2013", cardTime(null, oslo))
        assertEquals("23:30", cardTime(osloLateEvening, oslo))
        assertEquals("09:30", cardTime(osloLateEvening, sydney))
    }

    /** House style: no em dash (U+2014) in any string the app's Kotlin shows. */
    @Test fun `no em dash in a string literal of the app`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main/kotlin"), File(it, "app/src/main/kotlin"), File(it, "android/app/src/main/kotlin")) }
            .firstOrNull { it.isDirectory }
        assertTrue("src/main/kotlin not found", root != null)
        val offending = root!!.walkTopDown().filter { it.name.endsWith(".kt") }.flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, line) ->
                    val code = line.trim()
                    !code.startsWith("//") && !code.startsWith("*") && !code.startsWith("/*") &&
                        Regex("\"[^\"]*\u2014[^\"]*\"").containsMatchIn(code)
                }
                .map { (i, line) -> "${f.name}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertEquals(emptyList<String>(), offending)
    }
}

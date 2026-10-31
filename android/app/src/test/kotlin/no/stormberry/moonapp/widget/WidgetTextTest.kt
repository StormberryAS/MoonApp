package no.stormberry.moonapp.widget

import no.stormberry.moonapp.lunar.LunarDayKind
import no.stormberry.moonapp.lunar.MoonPhase
import no.stormberry.moonapp.lunar.MoonPhase.PrincipalPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The widgets' words and formats (1.0.8), exactly, and the house style they keep. */
class WidgetTextTest {

    private var saved: Locale = Locale.getDefault()

    /** The phone's language must not reach the widget's dates (D4): run as a Norwegian phone. */
    @Before fun norwegianPhone() { saved = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("nb-NO")) }
    @After fun restore() { Locale.setDefault(saved) }

    private val oslo = ZoneId.of("Europe/Oslo")
    private val today = LocalDate.parse("2026-10-30")

    private fun state(
        kind: LunarDayKind = LunarDayKind.NORMAL,
        rise: Instant? = Instant.parse("2026-10-30T17:42:00Z"),
        transit: Instant? = Instant.parse("2026-10-31T00:10:00Z"),
        set: Instant? = Instant.parse("2026-10-31T07:55:00Z"),
        zoneDiffers: Boolean = false,
    ) = WidgetState(
        placeLabel = "Bergen", zone = oslo, zoneDiffers = zoneDiffers, today = today,
        fraction = 0.78, phase = 0.36, percent = 78, phaseName = "Waxing Gibbous", litOnRight = true,
        dayKind = kind, rise = rise, transit = transit, set = set,
        phases = listOf(
            MoonPhase.PhaseEvent(PrincipalPhase.FULL, LocalDate.parse("2026-11-05"), Instant.parse("2026-11-05T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.LAST_QUARTER, LocalDate.parse("2026-11-12"), Instant.parse("2026-11-12T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.NEW, LocalDate.parse("2026-11-20"), Instant.parse("2026-11-20T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.FIRST_QUARTER, LocalDate.parse("2026-11-27"), Instant.parse("2026-11-27T13:00:00Z")),
        ),
    )

    @Test fun `percent at both ends`() {
        assertEquals("0%", WidgetText.percent(0))
        assertEquals("78%", WidgetText.percent(78))
        assertEquals("100%", WidgetText.percent(100))
    }

    @Test fun `times in the place's zone, an en dash when there is none`() {
        assertEquals("18:42", WidgetText.time(Instant.parse("2026-10-30T17:42:00Z"), oslo))
        assertEquals("02:42", WidgetText.time(Instant.parse("2026-10-30T17:42:00Z"), ZoneId.of("Asia/Tokyo")))
        assertEquals("–", WidgetText.time(null, oslo))
    }

    @Test fun `dates in British English whatever the phone's language`() {
        assertEquals("Thu 12 Nov", WidgetText.shortDate(LocalDate.parse("2026-11-12")))
        assertEquals("Thursday 12 November", WidgetText.longDate(LocalDate.parse("2026-11-12")))
        assertEquals("Today", WidgetText.phaseDate(today, today))
        assertEquals("Fri 20 Nov", WidgetText.phaseDate(LocalDate.parse("2026-11-20"), today))
        assertEquals("Fri 30 Oct", WidgetText.wideHeaderDate(state()))
        assertEquals("Fri 30 Oct · Europe/Oslo", WidgetText.wideHeaderDate(state(zoneDiffers = true)))
        assertEquals("Times shown in Europe/Oslo.", WidgetText.zoneNote(oslo))
        // The compact arrangement: no weekday.
        assertEquals("12 Nov", WidgetText.compactPhaseDate(LocalDate.parse("2026-11-12"), today))
        assertEquals("1 Dec", WidgetText.compactPhaseDate(LocalDate.parse("2026-12-01"), today))
        assertEquals("Today", WidgetText.compactPhaseDate(today, today))
    }

    /** A day the moon stays below the horizon shows no time, so no zone either (emulator check). */
    @Test fun `no zone note on a day without times`() {
        val down = state(kind = LunarDayKind.ALWAYS_DOWN, rise = null, transit = null, set = null, zoneDiffers = true)
        assertFalse(WidgetText.showsZoneNote(down))
        assertEquals("Fri 30 Oct", WidgetText.wideHeaderDate(down))
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. The moon stays below the horizon all day. " +
                "Next full moon Thursday 5 November. Next last quarter Thursday 12 November. " +
                "Next new moon Friday 20 November. Next first quarter Friday 27 November.",
            WidgetText.describeLarge(down),
        )
        // Always up has a transit, so the zone stays.
        val up = state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null, zoneDiffers = true)
        assertTrue(WidgetText.showsZoneNote(up))
        assertTrue(WidgetText.describeLarge(up).contains("Times shown in Europe/Oslo."))
        assertEquals("Fri 30 Oct · Europe/Oslo", WidgetText.wideHeaderDate(up))
        // Same zone as the phone: no note on the widget (the description still names it).
        assertFalse(WidgetText.showsZoneNote(state()))
        assertTrue(WidgetText.showsZoneNote(state(zoneDiffers = true)))
    }

    @Test fun `phase labels`() {
        assertEquals(
            listOf("New moon", "First quarter", "Full moon", "Last quarter"),
            PrincipalPhase.entries.map { WidgetText.phaseLabel(it) },
        )
    }

    @Test fun `content descriptions, word for word`() {
        assertEquals("Bergen: Waxing Gibbous, 78% lit.", WidgetText.describeSmall(state()))
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. Moonrise 18:42. Transit 01:10. Moonset 08:55.",
            WidgetText.describeMedium(state()),
        )
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. No moonrise today. No transit today. No moonset today.",
            WidgetText.describeMedium(state(rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. The moon is above the horizon all day. Transit 01:10.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null)),
        )
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. The moon is above the horizon all day. No transit today.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. The moon stays below the horizon all day.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_DOWN, rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Bergen: Waxing Gibbous, 78% lit. Moonrise 18:42. Transit 01:10. Moonset 08:55. " +
                "Times shown in Europe/Oslo. Next full moon Thursday 5 November. Next last quarter Thursday 12 November. " +
                "Next new moon Friday 20 November. Next first quarter Friday 27 November.",
            WidgetText.describeLarge(state()),
        )
        assertEquals(
            "Next full moon today.",
            WidgetText.describePhase(MoonPhase.PhaseEvent(PrincipalPhase.FULL, today, Instant.parse("2026-10-30T12:00:00Z")), today),
        )
    }

    /** The resource file in this module, found from the working directory Gradle gives the test. */
    private fun res(rel: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "app/src/main/res/$rel"), File(it, "android/app/src/main/res/$rel"), File(it, "src/main/res/$rel")) }
            .firstOrNull { it.exists() } ?: error("$rel not found above the working directory")

    @Test fun `no widget string carries an em dash or a double hyphen`() {
        val texts = WidgetText.ALL_FIXED + listOf(
            WidgetText.describeLarge(state()),
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null)),
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_DOWN)),
            WidgetText.describeMedium(state(rise = null, transit = null, set = null)),
        )
        val strings = Regex("<string name=\"widget_[^\"]*\">([^<]*)</string>").findAll(res("values/strings.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals("six widget picker strings and the placeholder hint", 7, strings.size)
        for (t in texts + strings) {
            assertFalse("em dash in: $t", t.contains('\u2014'))
            assertFalse("double hyphen in: $t", t.contains("--"))
        }
    }
}

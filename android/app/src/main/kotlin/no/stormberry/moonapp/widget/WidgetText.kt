package no.stormberry.moonapp.widget

import no.stormberry.moonapp.lunar.LunarDayKind
import no.stormberry.moonapp.lunar.MoonPhase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word and number format the widgets show at run time (1.0.8), kept here like the rest of
 * the app's text rather than in strings.xml (which holds only the six widget picker strings and the
 * placeholder hint the layouts show before the first render).
 * Pure, so WidgetTextTest pins each string.
 *
 * Dates are formatted in Locale.UK whatever the phone's language (decision D4), so a Norwegian
 * phone still reads "Thu 12 Nov" between the English labels. A missing time is an en dash, never
 * an em dash (decision D5). The short polar labels are the website's (D6); the large widget and
 * every content description use the app's own sentences.
 */
object WidgetText {

    // Labels. The long ones are the app's own (MoonTimesScreen); the short ones fit a 2x1 widget.
    const val MOONRISE = "Moonrise"
    const val TRANSIT = "Transit"
    const val MOONSET = "Moonset"
    const val RISE_SHORT = "Rise"
    const val SET_SHORT = "Set"
    const val TODAY = "Today"

    /** En dash, U+2013: the value of a time that does not happen today. */
    const val NO_TIME = "–"

    /** The website's short polar labels (D6). */
    const val ALWAYS_UP_SHORT = "Always Up"
    const val ALWAYS_DOWN_SHORT = "Always Down"

    /** The app's polar notice titles, word for word. */
    const val ALWAYS_UP_LONG = "The moon is above the horizon all day"
    const val ALWAYS_DOWN_LONG = "The moon stays below the horizon all day"

    /** Between the date and the zone in the wide header. */
    const val SEPARATOR = " · "

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

    fun phaseLabel(p: MoonPhase.PrincipalPhase): String = when (p) {
        MoonPhase.PrincipalPhase.NEW -> "New moon"
        MoonPhase.PrincipalPhase.FIRST_QUARTER -> "First quarter"
        MoonPhase.PrincipalPhase.FULL -> "Full moon"
        MoonPhase.PrincipalPhase.LAST_QUARTER -> "Last quarter"
    }

    /** "78%", rounded as the card rounds. */
    fun percent(n: Int): String = "$n%"

    /** "18:42" in the place's zone, or [NO_TIME]. */
    fun time(at: Instant?, zone: ZoneId): String = at?.atZone(zone)?.format(TIME) ?: NO_TIME

    /** "Thu 12 Nov". */
    fun shortDate(d: LocalDate): String = d.format(SHORT_DATE)

    /** "Thursday 12 November". */
    fun longDate(d: LocalDate): String = d.format(LONG_DATE)

    /** A phase date on the large widget: "Today" when it is today, else "Thu 12 Nov". */
    fun phaseDate(d: LocalDate, today: LocalDate): String = if (d == today) TODAY else shortDate(d)

    /**
     * A phase date in the compact arrangement, where the row also carries the phase name and a
     * 180 dp widget has no room for the weekday: "Today", else "12 Nov".
     */
    fun compactPhaseDate(d: LocalDate, today: LocalDate): String = if (d == today) TODAY else d.format(DAY_MONTH)

    /**
     * Whether the large widget shows the zone: only when the place's zone is not the phone's, and
     * not on a day the moon stays below the horizon, which shows no time at all.
     */
    fun showsZoneNote(s: WidgetState): Boolean = s.zoneDiffers && s.dayKind != LunarDayKind.ALWAYS_DOWN

    /** The large widget's note when the times are not on the phone's clock; the card's wording. */
    fun zoneNote(zone: ZoneId): String = "Times shown in ${zone.id}."

    /** The wide header's right side: the date, and the zone when [showsZoneNote]. */
    fun wideHeaderDate(s: WidgetState): String =
        shortDate(s.today) + if (showsZoneNote(s)) SEPARATOR + s.zone.id else ""

    // -------------------------------------------------------------- the 2x1's time lines

    /** Which accent a time line takes (MoonWidgets maps it to the app's palette). */
    enum class Tint { RISE, TRANSIT, SET, ALWAYS_UP, ALWAYS_DOWN }

    /** One line of the 2x1: "Rise 18:42", or a polar label with no value. */
    data class TimeLine(val label: String, val value: String?, val tint: Tint) {
        val text: String get() = if (value == null) label else "$label $value"
    }

    /**
     * The 2x1's lines, top to bottom: Rise, Transit and Set; on an always-up day the website's
     * "Always Up" and the transit; on an always-down day "Always Down" alone (no transit, as on
     * the card). The large widget's compact arrangement shows the same lines.
     */
    fun timeLines(s: WidgetState): List<TimeLine> {
        val transit = TimeLine(TRANSIT, time(s.transit, s.zone), Tint.TRANSIT)
        return when (s.dayKind) {
            LunarDayKind.NORMAL -> listOf(
                TimeLine(RISE_SHORT, time(s.rise, s.zone), Tint.RISE),
                transit,
                TimeLine(SET_SHORT, time(s.set, s.zone), Tint.SET),
            )
            LunarDayKind.ALWAYS_UP -> listOf(TimeLine(ALWAYS_UP_SHORT, null, Tint.ALWAYS_UP), transit)
            LunarDayKind.ALWAYS_DOWN -> listOf(TimeLine(ALWAYS_DOWN_SHORT, null, Tint.ALWAYS_DOWN))
        }
    }

    /**
     * The 2x1's text: its lines in ONE text view, one per line (1.0.8, Marcos 2026-10-30: the
     * three rows shrink together). A text view autosizes as a whole, so the lines always share
     * one text size; three separate views each shrank on their own, and "Transit 03:35" came out
     * smaller than "Rise" and "Set" on a narrow 2x1.
     */
    fun mediumText(s: WidgetState): String = timeLines(s).joinToString("\n") { it.text }

    // -------------------------------------------------------------- content descriptions

    /** "Bergen: Waxing Gibbous, 78% lit." */
    fun describeMoon(s: WidgetState): String = "${s.placeLabel}: ${s.phaseName}, ${percent(s.percent)} lit."

    /** The times, in full sentences, as TalkBack reads them. */
    fun describeTimes(s: WidgetState): String {
        fun event(name: String, at: Instant?): String =
            if (at != null) "$name ${time(at, s.zone)}." else "No ${name.lowercase(Locale.UK)} today."
        return when (s.dayKind) {
            LunarDayKind.ALWAYS_UP -> "$ALWAYS_UP_LONG. ${event(TRANSIT, s.transit)}"
            LunarDayKind.ALWAYS_DOWN -> "$ALWAYS_DOWN_LONG."
            LunarDayKind.NORMAL -> listOf(
                event(MOONRISE, s.rise),
                event(TRANSIT, s.transit),
                event(MOONSET, s.set),
            ).joinToString(" ")
        }
    }

    /** "Next full moon Thursday 5 November." or "Next full moon today." */
    fun describePhase(e: MoonPhase.PhaseEvent, today: LocalDate): String {
        val name = phaseLabel(e.phase).lowercase(Locale.UK)
        return if (e.date == today) "Next $name today." else "Next $name ${longDate(e.date)}."
    }

    fun describeSmall(s: WidgetState): String = describeMoon(s)

    fun describeMedium(s: WidgetState): String = describeMoon(s) + " " + describeTimes(s)

    /**
     * The 2x1's description, the zone the times are in (not on a day without times), and the
     * next four phases.
     */
    fun describeLarge(s: WidgetState): String {
        val zone = if (s.dayKind == LunarDayKind.ALWAYS_DOWN) emptyList() else listOf(zoneNote(s.zone))
        return (listOf(describeMedium(s)) + zone + s.phases.map { describePhase(it, s.today) }).joinToString(" ")
    }

    /** Every fixed string above, for the house-style check in WidgetTextTest. */
    val ALL_FIXED: List<String> = listOf(
        MOONRISE, TRANSIT, MOONSET, RISE_SHORT, SET_SHORT, TODAY, NO_TIME, ALWAYS_UP_SHORT,
        ALWAYS_DOWN_SHORT, ALWAYS_UP_LONG, ALWAYS_DOWN_LONG, SEPARATOR,
    ) + MoonPhase.PrincipalPhase.entries.map { phaseLabel(it) }
}

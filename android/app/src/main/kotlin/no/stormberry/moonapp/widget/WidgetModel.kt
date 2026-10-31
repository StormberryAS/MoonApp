package no.stormberry.moonapp.widget

import no.stormberry.moonapp.lunar.LunarDayKind
import no.stormberry.moonapp.lunar.LunarEvent
import no.stormberry.moonapp.lunar.MoonCalc
import no.stormberry.moonapp.lunar.MoonPhase
import no.stormberry.moonapp.ui.PlaceDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the three home screen widgets show, worked out once per refresh and shared by all of
 * them (1.0.8). Pure: no Android import, so WidgetModelTest runs it on the JVM.
 *
 * Every figure is the app's own, from the functions the times card calls, for TODAY IN THE
 * PLACE'S ZONE (decision D3): illumination and phase at 12:00 UTC of that date as on the card
 * (D1, [MoonPhase.noonUtc]), so the widget and the card for today always print the same
 * percentage; rise, transit and set from [MoonCalc.times] in the place's zone, never UTC (the
 * lesson of the web day-boundary bug); the polar case from [MoonCalc.dayKind]; and the four
 * principal phases from [MoonPhase.nextPhases].
 */
data class WidgetState(
    val placeLabel: String,
    val zone: ZoneId,
    /** The place's zone is not the phone's, so the times are not on the phone's clock. */
    val zoneDiffers: Boolean,
    val today: LocalDate,
    val fraction: Double,
    val phase: Double,
    val percent: Int,
    val phaseName: String,
    /** Which side of the disc is lit as the place sees it: waxing is on the right in the north. */
    val litOnRight: Boolean,
    val dayKind: LunarDayKind,
    val rise: Instant?,
    /** Null on an always-down day, where the card hides it too, and on a day with no culmination. */
    val transit: Instant?,
    val set: Instant?,
    val phases: List<MoonPhase.PhaseEvent>,
)

object WidgetModel {

    /**
     * The place's zone: the stored IANA id, or the phone's zone when there is none (manual
     * coordinates have no zone) or it is not one this device knows. The same fallback as the
     * times screen, so the two agree.
     */
    fun zoneOf(tz: String?, deviceZone: ZoneId): ZoneId = PlaceDay.zoneOf(tz, deviceZone)

    fun build(
        now: Instant,
        placeLabel: String,
        latDeg: Double,
        lonDeg: Double,
        tz: String?,
        deviceZone: ZoneId,
    ): WidgetState {
        val zone = zoneOf(tz, deviceZone)
        // The same "today" the times screen opens on (PlaceDay), so the app and the widget agree.
        val today = PlaceDay.today(zone, now)
        val illum = MoonCalc.illumination(MoonPhase.noonUtc(today))
        val times = MoonCalc.times(today, latDeg, lonDeg, zone)
        val kind = MoonCalc.dayKind(today, latDeg, lonDeg, zone)
        return WidgetState(
            placeLabel = placeLabel,
            zone = zone,
            zoneDiffers = zone.id != deviceZone.id,
            today = today,
            fraction = illum.fraction,
            phase = illum.phase,
            // Math.round, as the card (app.js:357).
            percent = Math.round(illum.fraction * 100).toInt(),
            phaseName = MoonPhase.name(illum.phase),
            litOnRight = MoonDisc.litOnRight(illum.phase, latDeg),
            dayKind = kind,
            rise = times[LunarEvent.MOONRISE],
            transit = if (kind == LunarDayKind.ALWAYS_DOWN) null else times[LunarEvent.LUNAR_TRANSIT],
            set = times[LunarEvent.MOONSET],
            phases = MoonPhase.nextPhases(today, zone),
        )
    }
}

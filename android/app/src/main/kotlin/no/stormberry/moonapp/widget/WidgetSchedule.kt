package no.stormberry.moonapp.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * When the widgets redraw next (1.0.8): at the place's next midnight, so the date and every figure
 * move to the new day, and in any case within three hours, which keeps the times correct after
 * anything the broadcasts miss. Pure, so WidgetScheduleTest covers the zones with odd days.
 *
 * MoonWidgets arms ONE inexact, non-wakeup alarm (AlarmManager.setWindow with RTC) at this
 * instant. It needs no permission in either edition. The system never delivers it early, and on a
 * sleeping phone it waits for the next wake, so the phone is never woken for a widget.
 */
object WidgetSchedule {

    /** At most this long between two refreshes while the phone is awake. */
    val MAX_GAP: Duration = Duration.ofHours(3)

    /** The window MoonWidgets gives the alarm; Android 12 and later widen a shorter one to 10 min. */
    val WINDOW: Duration = Duration.ofMinutes(10)

    /**
     * One second after the start of the next day in [zone], or [now] plus [MAX_GAP], whichever is
     * sooner. `atStartOfDay` handles 23 and 25 hour days and a day whose midnight does not exist
     * (it then starts at the first valid instant, such as 01:00).
     */
    fun nextRefresh(now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(1)
        val cap = now.plus(MAX_GAP)
        return if (midnight.isBefore(cap)) midnight else cap
    }
}

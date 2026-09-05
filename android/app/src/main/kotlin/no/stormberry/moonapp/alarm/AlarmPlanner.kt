package no.stormberry.moonapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import java.time.Instant

/**
 * System alarm planner for MoonApp.
 *
 * For entry-level programmers:
 * On Android, the AlarmManager system service wakes up the device at exact timestamps even if the app
 * is closed or the screen is off (Doze mode).
 * AlarmPlanner uses PendingIntents—tokens handed to the Android OS that authorize the system to trigger
 * our AlarmFireReceiver broadcast when the scheduled time arrives.
 */
object AlarmPlanner {

    // Every rule needs its OWN request code. A single shared constant meant every
    // PendingIntent compared equal, so arming a second alarm silently replaced the first
    // and only one alarm could ever exist. PendingIntent equality ignores extras, so the
    // request code is the only thing that separates them. Derived from the rule id, which
    // is stable across a reboot, unlike a list index. Fixed 2026-09-04.
    private fun requestCodeFor(ruleId: String): Int = ruleId.hashCode()

    /**
     * Schedules the next occurrence of a lunar alarm rule with the Android OS AlarmManager.
     *
     * @param context Android Application Context.
     * @param rule The active lunar alarm rule to arm.
     */
    fun scheduleAlarm(context: Context, rule: MoonAlarmRule) {
        val nextOccurrence = OccurrenceEngine.findNextOccurrence(rule) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmFireReceiver::class.java).apply {
            putExtra("RULE_ID", rule.id)
            putExtra("RULE_LABEL", rule.label)
            putExtra("EVENT_NAME", rule.event.displayName)
            putExtra("CITY_NAME", rule.cityName)
            putExtra("VIBRATE", rule.vibrate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(rule.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = nextOccurrence.toEpochMilli()

        // setAlarmClock, not setExactAndAllowWhileIdle. It is the API that declares this is
        // a user-facing alarm clock: it is exempt from Doze, it surfaces in the status bar and
        // in the system's next-alarm slot, and it is the concrete behaviour that justifies
        // USE_EXACT_ALARM to a Play reviewer. setExactAndAllowWhileIdle does none of that.
        val show = PendingIntent.getActivity(
            context,
            requestCodeFor(rule.id),
            Intent(context, no.stormberry.moonapp.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Guarded. On Android 12/12L the user can revoke SCHEDULE_EXACT_ALARM in Settings and
        // this throws SecurityException; some vendor ROMs also throw on a pending-alarm limit.
        // Unguarded, that exception propagated out of SystemEventReceiver.onReceive during the
        // post-boot re-arm, so one refusing rule abandoned every rule after it in the loop.
        // One alarm failing must not take the others with it.
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, show),
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    /**
     * Cancels the pending alarm for one rule.
     */
    fun cancelAlarm(context: Context, ruleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmFireReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(ruleId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

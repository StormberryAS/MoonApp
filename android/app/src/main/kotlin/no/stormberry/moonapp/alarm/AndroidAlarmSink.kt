package no.stormberry.moonapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import no.stormberry.moonapp.MainActivity

/**
 * The one [AlarmSink] that talks to `AlarmManager` (1.0.4). It only translates: every decision
 * about which rule rings when is made above it by [AlarmPlanner] and [OccurrenceEngine].
 *
 * **The fire intent keeps the identity it had in 1.0.3**: `AlarmFireReceiver` with no action and
 * no data, under the rule's request code. PendingIntent equality is action, data, type, class,
 * categories and request code, never extras, so an alarm armed by 1.0.3 is the same PendingIntent
 * and is replaced, not doubled, when 1.0.4 re-plans after the update. The extras now also carry
 * the instant the alarm was armed for, which the receiver needs to tell a ring from a repeat.
 */
class AndroidAlarmSink(context: Context) : AlarmSink {

    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager? = appContext.getSystemService(AlarmManager::class.java)

    /**
     * `setAlarmClock` first, unchanged from 1.0.3: it is the API that says this is an alarm clock,
     * exempt from Doze and shown in the status bar. When it throws (on Android 12 and 12L with exact
     * alarms switched off it throws SecurityException, and some vendors throw on a pending-alarm
     * limit), 1.0.3 only logged it and NOTHING was armed (A-2). Now the alarm is armed inexactly
     * instead with `setAndAllowWhileIdle`, which needs no grant and still fires in Doze, perhaps late.
     */
    override fun arm(planned: PlannedAlarm): ArmResult {
        val manager = alarmManager ?: return ArmResult.FAILED
        val operation = PendingIntent.getBroadcast(
            appContext,
            planned.requestCode,
            fireIntent(appContext, planned, guardPath = null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = planned.fireAt.toEpochMilli()
        val result = FireRules.armWithFallback(
            exact = { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(planned.requestCode)), operation) },
            inexact = { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation) },
            onExactFailure = { e -> Log.w(TAG, "exact alarm refused for ${planned.rule.id}, arming it inexactly", e) },
        )
        if (result == ArmResult.FAILED) Log.e(TAG, "could not arm ${planned.rule.id} at all")
        return result
    }

    /**
     * The backstop for the nearest alarm on Android 12 and 12L (A-2), armed twice under its own
     * addresses, as SunApp's: `setAndAllowWhileIdle` survives Doze but may come up to an hour late,
     * and `setWindow` with the shortest window Android 12 allows (ten minutes) arrives within it
     * while the phone is awake. Switching exact alarms off cancels exact alarms only, so both
     * survive it. Whichever arrives first rings; the receiver then finds the other, and the exact
     * one if it still comes, already rung.
     */
    override fun guard(next: PlannedAlarm?) {
        val manager = alarmManager ?: return
        for ((path, code) in GUARDS) {
            try {
                if (next != null) {
                    val operation = PendingIntent.getBroadcast(
                        appContext, code, fireIntent(appContext, next, guardPath = path),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val at = next.fireAt.toEpochMilli()
                    if (path == GUARD_IDLE) {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                    } else {
                        manager.setWindow(AlarmManager.RTC_WAKEUP, at, GUARD_WINDOW_MS, operation)
                    }
                } else {
                    PendingIntent.getBroadcast(
                        appContext, code,
                        Intent(appContext, AlarmFireReceiver::class.java)
                            .setAction(AlarmFireReceiver.ACTION_GUARD)
                            .setData("moonapp://$path".toUri()),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )?.let { pending ->
                        manager.cancel(pending)
                        pending.cancel()
                    }
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not set the backstop $path", e)
            }
        }
    }

    override fun cancel(requestCode: Int) {
        try {
            val pending = PendingIntent.getBroadcast(
                appContext, requestCode,
                Intent(appContext, AlarmFireReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            alarmManager?.cancel(pending)
            pending.cancel()
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not cancel request code $requestCode", e)
        }
    }

    /**
     * Where the system sends the person who taps the next-alarm chip. The same PendingIntent as
     * 1.0.3 (MainActivity under the rule's request code).
     */
    private fun showIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        appContext,
        requestCode,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "MoonAlarmSink"

        const val GUARD_IDLE = "guard/idle"
        const val GUARD_WINDOW = "guard/window"

        /** The two backstops, each with its own address and request code. */
        private val GUARDS = listOf(GUARD_IDLE to 0x4D30, GUARD_WINDOW to 0x4D32)

        /** The shortest window `setWindow` honours on Android 12. */
        private const val GUARD_WINDOW_MS = 10L * 60_000L

        /**
         * The fire intent for [planned]: the 1.0.3 identity (no action, no data) for the alarm
         * itself, or the backstop's own action and address when [guardPath] is set.
         */
        fun fireIntent(context: Context, planned: PlannedAlarm, guardPath: String?): Intent =
            Intent(context, AlarmFireReceiver::class.java).apply {
                if (guardPath != null) {
                    action = AlarmFireReceiver.ACTION_GUARD
                    data = "moonapp://$guardPath".toUri()
                }
                putExtra(AlarmFireReceiver.EXTRA_RULE_ID, planned.rule.id)
                putExtra(AlarmFireReceiver.EXTRA_LABEL, planned.rule.label)
                putExtra(AlarmFireReceiver.EXTRA_EVENT, planned.rule.event.displayName)
                putExtra(AlarmFireReceiver.EXTRA_CITY, planned.rule.cityName)
                putExtra(AlarmFireReceiver.EXTRA_VIBRATE, planned.rule.vibrate)
                putExtra(AlarmFireReceiver.EXTRA_FIRE_AT, planned.fireAt.toEpochMilli())
            }
    }
}

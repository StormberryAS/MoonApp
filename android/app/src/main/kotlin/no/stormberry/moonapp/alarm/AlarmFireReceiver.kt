package no.stormberry.moonapp.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver triggered when the Android system fires a scheduled lunar alarm.
 *
 * For entry-level programmers:
 * A BroadcastReceiver is an entry point that listens for system events or scheduled intents.
 * When the alarm minute arrives, Android activates this receiver even if MoonApp is closed.
 * It grabs a temporary CPU WakeLock so the phone doesn't sleep while launching the RingActivity UI.
 *
 * 1.0.4 (ported from SunApp 1.1.4, without snooze): it also receives the Android 12 backstop,
 * Dismiss from the notifications and the ring screen, and the backup ring notification going
 * away. It must never throw, so every action runs inside one catch. When an alarm arrives:
 *  1. [FireRules.decide]: more than two seconds early (the clock moved) or already rung (the
 *     backstop and the exact alarm both arriving, or a re-arm after the clock was set back) means
 *     nothing rings; when the record cannot be read, it rings;
 *  2. an on-time ring is noted as the rule's last ring;
 *  3. every rule is re-planned, inside its own catch. 1.0.3 re-armed only this rule, and only
 *     after the service had started, so a refused start lost every later occurrence too;
 *  4. the ringing service starts, or, when Android refuses (an inexact alarm grants no exemption
 *     to start a foreground service: Android 12 and 12L with exact alarms off, A-2), the backup
 *     ring notification rings instead.
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                null, ACTION_GUARD -> onAlarmFired(context, intent)
                ACTION_DISMISS -> AlarmService.dismiss(context)
                ACTION_BACKUP_GONE -> onBackupGone(intent)
                else -> Log.w(TAG, "ignoring unexpected action ${intent.action}")
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not handle ${intent.action}", e)
        }
    }

    private fun onAlarmFired(context: Context, intent: Intent) {
        // Wake CPU briefly to guarantee smooth transition to foreground service and UI
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoonApp:AlarmFireWakeLock")
            .acquire(3000L) // 3-second temporary lock

        val now = System.currentTimeMillis()
        val armedAt = intent.getLongExtra(EXTRA_FIRE_AT, 0L)
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val lastRung = ruleId?.let {
            try {
                AlarmCapability.lastRung(context, it)
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not read the last ring of $it; ringing", e)
                null
            }
        }
        val decision = FireRules.decide(armedAt, now, lastRung)

        // Noted before the re-plan, so the re-plan arms the occurrence after this one even when
        // this one arrived a moment early. Only an on-time ring is noted: one that arrived late
        // because the clock jumped forward past it rang at the wrong real time.
        if (decision == FireDecision.RING && ruleId != null &&
            FireRules.shouldRecordRing(armedAt, now, inexact = intent.action == ACTION_GUARD || !exactAllowed(context))
        ) {
            runCatching { AlarmCapability.markRung(context, ruleId, armedAt) }
                .onFailure { Log.w(TAG, "could not note the ring of $ruleId", it) }
        }

        // RE-ARM. AlarmManager fires a one-shot: without this an alarm rings once and is gone
        // forever. Recompute from the saved rules rather than adding a fixed 24 hours, because
        // moonrise drifts roughly 50 minutes a day. Fixed 2026-09-04; since 1.0.4 every rule, before
        // the ring and inside its own catch, so the schedule survives whatever happens next.
        try {
            AlarmPlanner.replan(context)
        } catch (e: RuntimeException) {
            Log.e(TAG, "re-planning after a fire failed", e)
        }

        when (decision) {
            FireDecision.RING -> ring(context, AlarmService.alarmFor(intent, now))
            FireDecision.EARLY -> Log.w(TAG, "alarm arrived ${armedAt - now} ms early, treating it as a clock jump")
            FireDecision.ALREADY_RUNG -> Log.w(TAG, "this occurrence has rung already; not ringing it twice")
        }

        // The ring screen is raised by the service's full-screen-intent notification, NOT from
        // here. A BroadcastReceiver calling startActivity is a background activity start and
        // Android 10+ blocks it silently. See AlarmService.
    }

    /** Whether exact alarms are allowed now: always before Android 12. Unreadable counts as not. */
    private fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true }.getOrDefault(false)

    /**
     * Start the ringing service; when Android refuses, ring through the backup notification
     * instead (1.0.4, A-2). 1.0.3 let the exception escape, so nothing rang and nothing showed.
     */
    private fun ring(context: Context, alarm: RingingAlarm) {
        try {
            ContextCompat.startForegroundService(context, AlarmService.ringIntent(context, alarm))
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not start the ringing service; ringing through the backup notification", e)
            AlarmService.postBackupRing(context, alarm)
        }
    }

    /** The backup ring notification was swiped away or timed out: it no longer rings. */
    private fun onBackupGone(intent: Intent) {
        val alarm = AlarmService.alarmFrom(intent)
        val current = AlarmService.ringing.value
        if (current == null || current.key == alarm.key) AlarmService.setBackup(null)
    }

    companion object {
        private const val TAG = "MoonAlarmFire"

        /** Sent by the Android 12 and 12L backstop of the nearest alarm (1.0.4, A-2). */
        const val ACTION_GUARD = "no.stormberry.moonapp.action.ALARM_GUARD"

        /** Sent by Dismiss on the notifications and the ring screen (1.0.4). */
        const val ACTION_DISMISS = "no.stormberry.moonapp.action.DISMISS"

        /** Sent when the backup ring notification is swiped away or times out (1.0.4). */
        const val ACTION_BACKUP_GONE = "no.stormberry.moonapp.action.BACKUP_GONE"

        // The extras of the fire intent; the first five unchanged since 1.0.3.
        const val EXTRA_RULE_ID = "RULE_ID"
        const val EXTRA_LABEL = "RULE_LABEL"
        const val EXTRA_EVENT = "EVENT_NAME"
        const val EXTRA_CITY = "CITY_NAME"
        const val EXTRA_VIBRATE = "VIBRATE"

        /** The instant the alarm was armed for (1.0.4); absent (0) on an alarm armed by 1.0.3. */
        const val EXTRA_FIRE_AT = "FIRE_AT"
    }
}

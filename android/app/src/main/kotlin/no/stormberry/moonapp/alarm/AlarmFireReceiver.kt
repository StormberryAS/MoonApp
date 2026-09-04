package no.stormberry.moonapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * BroadcastReceiver triggered when the Android system fires a scheduled lunar alarm.
 *
 * For entry-level programmers:
 * A BroadcastReceiver is an entry point that listens for system events or scheduled intents.
 * When the alarm minute arrives, Android activates this receiver even if MoonApp is closed.
 * It grabs a temporary CPU WakeLock so the phone doesn't sleep while launching the RingActivity UI.
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra("RULE_ID")
        val label = intent.getStringExtra("RULE_LABEL") ?: "Lunar Alarm"
        val eventName = intent.getStringExtra("EVENT_NAME") ?: "Moon Event"
        val cityName = intent.getStringExtra("CITY_NAME") ?: "Local"
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        // Wake CPU briefly to guarantee smooth transition to foreground service and UI
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MoonApp:AlarmFireWakeLock"
        )
        wakeLock.acquire(3000L) // 3-second temporary lock

        // 1. Start foreground ringing service (audio playback)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("RULE_LABEL", label)
            putExtra("EVENT_NAME", eventName)
            putExtra("CITY_NAME", cityName)
            putExtra("VIBRATE", vibrate)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // RE-ARM. AlarmManager fires a one-shot: without this an alarm rings once and is gone
        // forever, while README.md advertised "Daily Auto-Recompute". Recompute from the saved
        // rule rather than adding a fixed 24 hours, because moonrise drifts roughly 50 minutes
        // a day and a fixed interval would walk off the event within a week. Fixed 2026-09-04.
        if (ruleId != null) {
            AlarmCapability.loadRules(context)
                .firstOrNull { it.id == ruleId && it.enabled }
                ?.let { AlarmPlanner.scheduleAlarm(context, it) }
        }

        // 2. The ring screen is raised by the service's full-screen-intent notification,
        // NOT from here. A BroadcastReceiver calling startActivity is a background activity
        // start and Android 10+ blocks it silently, so this used to do nothing at all on
        // any modern device. See buildForegroundNotification in AlarmService.
    }
}

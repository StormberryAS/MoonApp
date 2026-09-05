package no.stormberry.moonapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * System event receiver that re-computes and re-arms lunar alarms across system changes.
 *
 * For entry-level programmers:
 * When a phone reboots or changes timezones (e.g. flying across borders), all pending system alarms
 * are wiped from memory.
 * SystemEventReceiver listens for OS signals like `BOOT_COMPLETED` or `TIMEZONE_CHANGED`, recalculates the
 * new moon event times for the user's location, and re-arms the AlarmManager.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            // Android 12 and 12L only, which is exactly the range SCHEDULE_EXACT_ALARM is
            // capped to in the manifest (maxSdkVersion=32). Revoking exact alarms in Settings
            // makes the platform cancel every one of ours; granting it back sends this
            // broadcast. It was already in the manifest filter and the when-block dropped it,
            // so nothing was re-armed until the next reboot and the alarms the user had just
            // re-enabled stayed silent.
            ACTION_EXACT_ALARM_STATE_CHANGED -> {
                AlarmCapability.rearmAllAlarms(context)
            }
        }
    }

    private companion object {
        /**
         * `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` as a literal.
         * The constant is API 31 and minSdk here is 24, so referencing it directly would not
         * compile against the lower bound. The string is part of the platform's public API
         * surface and is what the manifest filter already names.
         */
        const val ACTION_EXACT_ALARM_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}

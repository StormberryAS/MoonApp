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
            Intent.ACTION_LOCALE_CHANGED -> {
                // Re-evaluate stored lunar alarm rules
                AlarmCapability.rearmAllAlarms(context)
            }
        }
    }
}

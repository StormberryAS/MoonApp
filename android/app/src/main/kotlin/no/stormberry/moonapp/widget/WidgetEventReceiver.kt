package no.stormberry.moonapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Redraws the widgets when the day or the clock may have moved under them (1.0.8):
 *
 * - [MoonWidgets.ACTION_REFRESH]: the inexact refresh alarm (the place's midnight, or three hours);
 * - TIME_SET and TIMEZONE_CHANGED: the clock or the phone's zone changed. A place with no stored
 *   zone uses the phone's, and the "Times shown in" note depends on it;
 * - MY_PACKAGE_REPLACED: MoonApp was updated.
 *
 * TIME_SET and TIMEZONE_CHANGED are on Android's list of implicit broadcasts that manifest
 * receivers still get from Android 8. DATE_CHANGED is not, which is why the widgets do not rely on
 * it. No permission is needed for any of them.
 *
 * NOT exported: the system sends TIME_SET, TIMEZONE_CHANGED and MY_PACKAGE_REPLACED from its own
 * uid, which reaches a receiver that is not exported, and the refresh alarm is our own
 * PendingIntent. So no other app can start a refresh in a loop. Disabled in the manifest and
 * enabled only while a widget is placed ([MoonWidgets.setEventReceiver]), so with no widget the
 * system never starts the app for a clock change. Any other action is ignored.
 */
class WidgetEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            MoonWidgets.ACTION_REFRESH,
            -> MoonWidgets.refreshInBackground(this, context)
            else -> Unit
        }
    }
}

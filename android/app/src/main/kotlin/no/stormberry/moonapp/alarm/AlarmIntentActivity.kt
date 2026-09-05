package no.stormberry.moonapp.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import no.stormberry.moonapp.MainActivity

/**
 * Answers the two standard AlarmClock intents MoonApp can actually honour.
 *
 * ### What changed and why
 *
 * The manifest used to advertise SET_ALARM, SHOW_ALARMS, DISMISS_ALARM and SNOOZE_ALARM, and
 * this activity forwarded all four to MainActivity's home screen and finished. So any app or
 * assistant that resolved one could pick MoonApp, and the request vanished: "set an alarm for
 * seven" opened a moon-times screen and created nothing, and "dismiss the alarm" left the
 * alarm ringing.
 *
 * SET_ALARM and SNOOZE_ALARM are gone from the manifest because MoonApp cannot implement
 * them honestly: its alarms are anchored to a lunar event, not a clock time, so there is no
 * sensible reading of either. Registering for an action and dropping it is worse than not
 * registering for it, because the chooser still offers the app.
 *
 * The two that remain are answerable, and this activity answers them.
 */
class AlarmIntentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            // Stop a ringing alarm. Routed to the service rather than to the UI, so it works
            // whether or not RingActivity is on screen.
            "android.intent.action.DISMISS_ALARM" -> {
                startService(
                    Intent(this, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_STOP_ALARM)
                )
            }

            // Open the app on the alarms tab, which is what "show my alarms" means here.
            "android.intent.action.SHOW_ALARMS" -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_ALARMS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }

            else -> {
                // Nothing else is advertised. If an action arrives anyway, open the app rather
                // than doing something the caller did not ask for.
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
        finish()
    }
}

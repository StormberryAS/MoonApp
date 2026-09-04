package no.stormberry.moonapp.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import no.stormberry.moonapp.MainActivity

/**
 * Trampoline activity answering standard Android AlarmClock intents.
 *
 * For entry-level programmers:
 * Android allows voice assistants or calendar apps to send standard system intents like
 * `android.intent.action.SET_ALARM`.
 * AlarmIntentActivity acts as an invisible entry point that receives these requests, directs them to
 * MainActivity, and immediately finishes without flickering on screen.
 */
class AlarmIntentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forward request to MainActivity with alarm intent action flag
        val forwardIntent = Intent(this, MainActivity::class.java).apply {
            action = intent?.action
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(forwardIntent)
        finish()
    }
}

package no.stormberry.moonapp.alarm

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.stormberry.moonapp.ui.MoonAppTheme

/**
 * Full-screen Ring Activity shown over the lock screen when a lunar alarm triggers.
 *
 * For entry-level programmers:
 * When an alarm goes off while the device is locked, Android launches this activity over the lock screen
 * using special flags (`showWhenLocked` and `turnScreenOn`).
 * The user can see the lunar details and tap "Dismiss Alarm" to silence the tone.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn on screen and show over keyguard/lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // KEEP_SCREEN_ON on EVERY api level, not only below 27. setShowWhenLocked and
        // setTurnScreenOn turn the display on; neither keeps it on. On any modern phone the
        // screen therefore timed out after the usual 15 to 30 seconds while the alarm went on
        // ringing, leaving a half-awake user tapping a black screen with no Dismiss button.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Back must not remove the only screen that can stop the alarm. Without this, a
        // reflexive back swipe on a ringing phone dismissed the UI and left the tone and the
        // vibration running with the notification's Dismiss action as the only way out.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Deliberately nothing. Dismiss is the way out of this screen.
            }
        })

        adoptBackup(intent)

        setContent {
            // 1.0.4: the screen shows what rings now (AlarmService.ringing: the service's alarm,
            // else the backup ring notification's) and closes itself when nothing rings any more,
            // including after the ten-minute auto-silence (M-4: 1.0.3 stayed up with a Dismiss
            // button for nothing). Opened by MainActivity too, while an alarm rings (A-1).
            val ringing = AlarmService.ringing.value
            LaunchedEffect(ringing) {
                if (ringing == null) {
                    finish()
                    return@LaunchedEffect
                }
                // A backup ring has no service to end it: look again at its ring timeout.
                val left = ringing.startedAtMillis + AlarmService.AUTO_SILENCE_MS - System.currentTimeMillis()
                if (left > 0L) delay(left + TIMEOUT_SLACK_MS)
                if (AlarmService.current() == null) {
                    AlarmService.setBackup(null)
                    finish()
                }
            }
            val label = ringing?.label ?: intent.getStringExtra("RULE_LABEL") ?: "Lunar Alarm"
            val eventName = ringing?.eventName ?: intent.getStringExtra("EVENT_NAME") ?: "Moon Event"
            val cityName = ringing?.cityName ?: intent.getStringExtra("CITY_NAME") ?: "Local"

            // MoonAppTheme, not a bare MaterialTheme, so this screen is set in Inter like the
            // rest of the app. Every colour below is explicit, so the theme's palette changes
            // nothing here.
            MoonAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Deep Night Indigo
                ) {
                    // 1.0.4: the text scrolls and Dismiss stays pinned below it. At font size 2.0 in
                    // landscape the centred column of 1.0.3 pushed Dismiss off the bottom of the
                    // screen with no way to reach it (emulator, 23 October 2026; SunApp's verify
                    // found the same), leaving only the notification's Dismiss.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val room = maxHeight
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .heightIn(min = room),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "🌕",
                                    fontSize = 72.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "$eventName • $cityName",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                dismissAlarm()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8) // Lunar Cyan
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                        ) {
                            Text(
                                text = "Dismiss Alarm",
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        adoptBackup(intent)
    }

    /**
     * Opened by the backup ring notification, perhaps in a process that did not post it: the alarm
     * comes with the intent, and is shown until its ring timeout unless the service rings.
     */
    private fun adoptBackup(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_BACKUP, false) != true) return
        val alarm = AlarmService.alarmFrom(intent)
        val current = AlarmService.current()
        if (current != null && current.key == alarm.key) return
        if (current == null && System.currentTimeMillis() < alarm.startedAtMillis + AlarmService.AUTO_SILENCE_MS) {
            AlarmService.setBackup(alarm)
        }
    }

    private fun dismissAlarm() {
        // Stops the service and the backup ring notification alike (1.0.4); the ring screen
        // would close by itself once nothing rings, and finishing here makes it immediate.
        AlarmService.dismiss(this)
        finish()
    }

    companion object {
        /** On the backup ring notification's intent: the alarm rides along in the extras (1.0.4). */
        const val EXTRA_BACKUP = "no.stormberry.moonapp.extra.BACKUP"

        private const val TIMEOUT_SLACK_MS = 500L
    }
}

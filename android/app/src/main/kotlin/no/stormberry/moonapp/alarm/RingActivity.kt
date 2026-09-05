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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

        val label = intent.getStringExtra("RULE_LABEL") ?: "Lunar Alarm"
        val eventName = intent.getStringExtra("EVENT_NAME") ?: "Moon Event"
        val cityName = intent.getStringExtra("CITY_NAME") ?: "Local"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Deep Night Indigo
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
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

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = {
                                dismissAlarm()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8) // Lunar Cyan
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(56.dp)
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

    private fun dismissAlarm() {
        // Stop foreground media service
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }
}

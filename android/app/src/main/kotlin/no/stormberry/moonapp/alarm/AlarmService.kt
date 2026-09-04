package no.stormberry.moonapp.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground Service responsible for audio ringing and vibration during an active lunar alarm.
 *
 * For entry-level programmers:
 * A Foreground Service runs in the background with a persistent notification, ensuring that Android OS
 * does not kill the ringtone mid-song while the user wakes up.
 */
class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val CHANNEL_ID = "moonapp_alarm_channel"
        private const val NOTIFICATION_ID = 8802
        const val ACTION_STOP_ALARM = "no.stormberry.moonapp.STOP_ALARM"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra("RULE_LABEL") ?: "Lunar Alarm"
        val eventName = intent?.getStringExtra("EVENT_NAME") ?: "Moon Event"
        val cityName = intent?.getStringExtra("CITY_NAME") ?: "Local"
        val vibrate = intent?.getBooleanExtra("VIBRATE", true) ?: true

        val notification = buildForegroundNotification(label, eventName, cityName)
        startForeground(NOTIFICATION_ID, notification)

        // Play system default alarm ringtone
        playRingtone()

        // Trigger vibration if enabled
        if (vibrate) {
            startVibration()
        }

        return START_STICKY
    }

    private fun playRingtone() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            // minSdk is 24, so the old API-21 guard was always true and used an illegal
            // identifier (Build.VERSION_CODES.21). Set the attributes unconditionally.
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // An alarm that plays once and stops is not an alarm. Ringtone.isLooping
            // arrived in API 28; below that the handler below restarts it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildForegroundNotification(label: String, eventName: String, cityName: String): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // THE FULL-SCREEN INTENT IS THE MECHANISM, not a direct startActivity. A
        // BroadcastReceiver calling startActivity is a background activity start, which
        // Android 10 and later silently block, so the ring screen never appeared and the
        // alarm could sound with nothing on screen to stop it. This is also the whole
        // reason USE_FULL_SCREEN_INTENT is declared; before 2026-09-04 it was requested
        // and never used. On a locked device the system shows the ring activity; on an
        // unlocked one it shows a heads-up notification, which is the correct behaviour.
        val ringIntent = Intent(this, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("RULE_LABEL", label)
            putExtra("EVENT_NAME", eventName)
            putExtra("CITY_NAME", cityName)
        }
        val ringPendingIntent = PendingIntent.getActivity(
            this,
            1,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$label ($eventName)")
            .setContentText("Location: $cityName. Tap to dismiss.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(ringPendingIntent, true)
            .setContentIntent(ringPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MoonApp Lunar Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active ringing notifications for MoonApp lunar alarms"
                setSound(null, null) // Audio handled via Ringtone object
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

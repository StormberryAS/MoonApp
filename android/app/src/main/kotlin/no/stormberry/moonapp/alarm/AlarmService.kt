package no.stormberry.moonapp.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * What is ringing now, as far as the ring screen and MainActivity need to know (1.0.4).
 * [startedAtMillis] is when this ring began, which its ten-minute timeout counts from.
 */
data class RingingAlarm(
    val ruleId: String?,
    val label: String,
    val eventName: String,
    val cityName: String,
    val vibrate: Boolean,
    val fireAtMillis: Long,
    val startedAtMillis: Long,
) {
    /** One ring of one alarm. */
    val key: String get() = "${ruleId.orEmpty()}@$fireAtMillis"
}

/**
 * Foreground Service responsible for audio ringing and vibration during an active lunar alarm.
 *
 * For entry-level programmers:
 * A Foreground Service runs in the background with a persistent notification, ensuring that Android OS
 * does not kill the ringtone mid-song while the user wakes up.
 *
 * 1.0.4, the EclipseTracker fix B items that apply to MoonApp (ported from SunApp 1.1.4):
 *  - A-1: the alarm rings even when notifications are off or the ring channel is turned down,
 *    when the person sees nothing; [ringing] then lets MainActivity open the ring screen, so
 *    opening MoonApp is always a way to stop it.
 *  - A-2 and A-4: when the ringing cannot run here (Android refused the foreground, or the
 *    receiver could not start this service at all), the backup ring notification rings
 *    instead: the phone's alarm sound, repeated, on its own channel, with the ring screen as its
 *    full-screen intent and Dismiss, gone after the ring timeout.
 *  - A-5: a second alarm arriving while one rings stops the first one's tone and vibration
 *    before starting its own. Until 1.0.4 the first Ringtone was overwritten, not stopped, and on
 *    Android 9 and later it looped on after Dismiss and after the auto-silence until the process
 *    died. No Missed notice is posted for the replaced alarm (decision D-7: MoonApp keeps one
 *    notification, and a notice beside a ring is what silenced SunApp 1.1.2's ring screen).
 *  - M-1: on Android 7 to 8.1 the tone is restarted every three seconds by a runnable that
 *    onStartCommand used to remove straight after posting it, so the tone played once.
 */
class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    /** A-4: this service holds the backup ring notification in the foreground (no tone of its own). */
    private var holdsBackup = false

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarm = alarmFrom(intent)

        // A-5 and M-1: whatever rang before (another alarm one minute earlier, or nothing) is
        // stopped first, the API 24 to 27 re-loop runnable with it, and only then is the new
        // tone started. 1.0.3 started the new tone and then cleared the handler, which removed
        // the re-loop it had just posted (M-1) and left the first Ringtone playing (A-5).
        stopRinging()
        setBackup(null)
        current.value = alarm

        if (!startInForeground(buildForegroundNotification(this, alarm))) {
            // A-4: without the foreground this service cannot ring, and 1.0.3 crashed here. The
            // backup ring notification rings instead; held in the foreground by this service if
            // Android allows that, which also settles startForegroundService's demand.
            Log.w(TAG, "ringing through the backup notification instead")
            current.value = null
            if (ringBackupFromService(alarm)) {
                holdsBackup = true
                handler.postDelayed({ stopSelf() }, AUTO_SILENCE_MS)
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }
        holdsBackup = false

        // Play system default alarm ringtone
        playRingtone()

        // Trigger vibration if enabled
        if (alarm.vibrate) {
            startVibration()
        }

        handler.postDelayed({ stopSelf() }, AUTO_SILENCE_MS)

        // START_NOT_STICKY, not START_STICKY. When Android reclaims the service under memory
        // pressure or a vendor task killer takes it, START_STICKY restarts it with a NULL
        // Intent: every getStringExtra falls back and the app rings again, at an arbitrary
        // later time, with the generic text and no rule behind it. An alarm that re-rings by
        // itself hours later is worse than one that does not ring at all. The alarm is
        // re-armed from the stored rules by SystemEventReceiver, which is the correct path.
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification): Boolean = try {
        // Debug builds only: lets the emulator test force the A-4 path (TestHooks; release has none).
        if (TestHooks.failForeground(this)) throw IllegalStateException("test hook: the foreground is refused")
        startForeground(NOTIFICATION_ID, notification)
        true
    } catch (e: RuntimeException) {
        // ForegroundServiceStartNotAllowedException on 12 and later, SecurityException on 14 and
        // later if the declared type is ever refused. Neither is worth crashing an alarm over.
        Log.e(TAG, "could not enter the foreground", e)
        false
    }

    /** A-4: the backup ring held in the foreground by this service (true), else posted plainly (false). */
    private fun ringBackupFromService(alarm: RingingAlarm): Boolean {
        setBackup(alarm)
        val backup = buildBackupNotification(this, alarm)
        return try {
            startForeground(NOTIFICATION_ID, backup)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not enter the foreground with the backup either", e)
            postPlain(this, backup)
            false
        }
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
            } else {
                // The re-loop for API 24 to 27. It runs after onStartCommand returns, which is why
                // nothing after this point may clear the handler (M-1, fixed in 1.0.4: 1.0.3
                // cleared it straight after this call, so the tone played once).
                handler.post(object : Runnable {
                    override fun run() {
                        val r = ringtone ?: return
                        if (!r.isPlaying) r.play()
                        handler.postDelayed(this, RELOOP_MS)
                    }
                })
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stops the tone, the vibration and the re-loop runnable. The handler first, so the API 24 to
     * 27 re-loop cannot restart a tone that has just been stopped.
     */
    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        current.value = null
        if (holdsBackup) {
            setBackup(null)
            cancelRingNotification(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MoonAlarmService"

        /** The ringing alarm's channel, silent: the service plays the sound itself. Unchanged since 1.0.1. */
        const val CHANNEL_ID = "moonapp_alarm_channel"

        /** 1.0.4 (A-2, A-4): the backup ring notification's channel, with the phone's alarm sound of its own. */
        const val CHANNEL_BACKUP = "moonapp_alarm_backup"

        private const val NOTIFICATION_ID = 8802
        const val ACTION_STOP_ALARM = "no.stormberry.moonapp.STOP_ALARM"

        private const val EXTRA_RULE_ID = AlarmFireReceiver.EXTRA_RULE_ID
        private const val EXTRA_LABEL = AlarmFireReceiver.EXTRA_LABEL
        private const val EXTRA_EVENT = AlarmFireReceiver.EXTRA_EVENT
        private const val EXTRA_CITY = AlarmFireReceiver.EXTRA_CITY
        private const val EXTRA_VIBRATE = AlarmFireReceiver.EXTRA_VIBRATE
        private const val EXTRA_FIRE_AT = AlarmFireReceiver.EXTRA_FIRE_AT
        private const val EXTRA_STARTED_AT = "STARTED_AT"

        private const val REQUEST_RING_SCREEN = 1
        private const val REQUEST_DISMISS = 2
        private const val REQUEST_RING_SCREEN_BACKUP = 3
        private const val REQUEST_BACKUP_GONE = 4

        /**
         * How long an unanswered alarm rings before it gives up.
         *
         * The upper bound is set by a phone in a bag on a train, where an alarm nobody can
         * hear should eventually stop rather than flatten the battery. Ten minutes, matching
         * SunApp. Before this existed the tone looped and the vibrator ran with repeat index
         * 0 and there was no timer anywhere in the app, so a missed alarm rang until someone
         * dismissed it or the battery died.
         */
        const val AUTO_SILENCE_MS = 10L * 60L * 1000L

        /** Ringtone.isLooping is API 28+; below that the tone is restarted on this period. */
        private const val RELOOP_MS = 3000L

        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

        /** What this service rings now, or null. */
        private val current = mutableStateOf<RingingAlarm?>(null)

        /** What the backup ring notification rings now, when this service could not (1.0.4). */
        private val backup = mutableStateOf<RingingAlarm?>(null)

        /**
         * The alarm ringing now, by this service or by the backup ring notification, for the ring
         * screen to show and close on, and for MainActivity to open it (1.0.4, A-1, M-4).
         */
        val ringing: State<RingingAlarm?> = derivedStateOf { current.value ?: backup.value }

        /** What rings now, the backup only until its ring timeout, or null. */
        fun current(now: Long = System.currentTimeMillis()): RingingAlarm? =
            current.value ?: backup.value?.takeIf { now < it.startedAtMillis + AUTO_SILENCE_MS }

        /** Sets (or clears) what the backup ring notification rings. */
        fun setBackup(alarm: RingingAlarm?) {
            backup.value = alarm
        }

        /**
         * Creates both channels if they do not exist. Idempotent, and called from every entry
         * point. Importance, sound and vibration belong to the person once a channel exists, and
         * re-creating it does not override their choices.
         */
        fun ensureChannels(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("MoonApp Lunar Alarms")
                    .setDescription("Active ringing notifications for MoonApp lunar alarms")
                    .setSound(null, null) // Audio handled via Ringtone object
                    .build(),
            )
            // Text T-5, approved by Marcos on 2026-10-23.
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_BACKUP, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Ringing alarms, backup")
                    .setDescription(
                        "Used when Android does not let MoonApp ring in full: the phone's alarm " +
                            "sound and the alarm on screen.",
                    )
                    .setSound(
                        Settings.System.DEFAULT_ALARM_ALERT_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setVibrationEnabled(true)
                    .setVibrationPattern(VIBRATION_PATTERN)
                    .setShowBadge(false)
                    .build(),
            )
        }

        /** The alarm to ring, from the fire intent's extras, which 1.0.3 already carried. */
        fun alarmFor(intent: Intent, now: Long): RingingAlarm = RingingAlarm(
            ruleId = intent.getStringExtra(EXTRA_RULE_ID),
            label = intent.getStringExtra(EXTRA_LABEL) ?: "Lunar Alarm",
            eventName = intent.getStringExtra(EXTRA_EVENT) ?: "Moon Event",
            cityName = intent.getStringExtra(EXTRA_CITY) ?: "Local",
            vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
            fireAtMillis = intent.getLongExtra(EXTRA_FIRE_AT, 0L).takeIf { it > 0L } ?: now,
            startedAtMillis = now,
        )

        private fun putAlarm(intent: Intent, alarm: RingingAlarm): Intent = intent
            .putExtra(EXTRA_RULE_ID, alarm.ruleId)
            .putExtra(EXTRA_LABEL, alarm.label)
            .putExtra(EXTRA_EVENT, alarm.eventName)
            .putExtra(EXTRA_CITY, alarm.cityName)
            .putExtra(EXTRA_VIBRATE, alarm.vibrate)
            .putExtra(EXTRA_FIRE_AT, alarm.fireAtMillis)
            .putExtra(EXTRA_STARTED_AT, alarm.startedAtMillis)

        /** The alarm carried by an intent [putAlarm] filled. */
        fun alarmFrom(intent: Intent): RingingAlarm {
            val now = System.currentTimeMillis()
            return alarmFor(intent, now).copy(startedAtMillis = intent.getLongExtra(EXTRA_STARTED_AT, now))
        }

        /** The service's start intent for [alarm]. */
        fun ringIntent(context: Context, alarm: RingingAlarm): Intent =
            putAlarm(Intent(context, AlarmService::class.java), alarm)

        /**
         * The ring screen. For the backup ring it carries the alarm, because the process that
         * shows the screen may not be the one that posted the notification.
         */
        private fun ringScreenIntent(context: Context, alarm: RingingAlarm, backup: Boolean): PendingIntent {
            val intent = Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putAlarm(intent, alarm)
            if (backup) intent.putExtra(RingActivity.EXTRA_BACKUP, true)
            return PendingIntent.getActivity(
                context,
                if (backup) REQUEST_RING_SCREEN_BACKUP else REQUEST_RING_SCREEN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Dismiss, as a broadcast to [AlarmFireReceiver] (1.0.4). Until then it started this service
         * with a stop command, which cannot reach a backup ring that has no service behind it.
         */
        private fun dismissPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISS,
            Intent(context, AlarmFireReceiver::class.java).setAction(AlarmFireReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun buildForegroundNotification(context: Context, alarm: RingingAlarm): Notification {
            // THE FULL-SCREEN INTENT IS THE MECHANISM, not a direct startActivity. A
            // BroadcastReceiver calling startActivity is a background activity start, which
            // Android 10 and later silently block, so the ring screen never appeared and the
            // alarm could sound with nothing on screen to stop it. This is also the whole
            // reason USE_FULL_SCREEN_INTENT is declared; before 2026-09-04 it was requested
            // and never used. On a locked device the system shows the ring activity; on an
            // unlocked one it shows a heads-up notification, which is the correct behaviour.
            val ring = ringScreenIntent(context, alarm, backup = false)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("${alarm.label} (${alarm.eventName})")
                .setContentText("Location: ${alarm.cityName}. Tap to dismiss.")
                .setSmallIcon(no.stormberry.moonapp.R.drawable.ic_stat_moon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(ring, true)
                .setContentIntent(ring)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent(context))
                .build()
        }

        /**
         * The backup ring notification (1.0.4, A-2 and A-4): what rings when this service cannot.
         * The phone's alarm sound from its own channel, repeated until answered (insistent), the
         * ring screen as its full-screen intent, Dismiss, and gone after the ring timeout. Swiping
         * it away, or its timeout, tells the receiver. Not silent: it is the alarm. The texts are
         * the ringing notification's own.
         */
        fun buildBackupNotification(context: Context, alarm: RingingAlarm): Notification {
            val ring = ringScreenIntent(context, alarm, backup = true)
            val gone = PendingIntent.getBroadcast(
                context,
                REQUEST_BACKUP_GONE,
                putAlarm(Intent(context, AlarmFireReceiver::class.java).setAction(AlarmFireReceiver.ACTION_BACKUP_GONE), alarm),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_BACKUP)
                .setContentTitle("${alarm.label} (${alarm.eventName})")
                .setContentText("Location: ${alarm.cityName}. Tap to dismiss.")
                .setSmallIcon(no.stormberry.moonapp.R.drawable.ic_stat_moon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setFullScreenIntent(ring, true)
                .setContentIntent(ring)
                .setDeleteIntent(gone)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent(context))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // No channels before Android 8: the sound and the vibration go on the notification.
                @Suppress("DEPRECATION")
                builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, AudioManager.STREAM_ALARM)
                    .setVibrate(VIBRATION_PATTERN)
            } else {
                builder.setTimeoutAfter(AUTO_SILENCE_MS)
            }
            val notification = builder.build()
            // The channel's sound plays once per alert; insistent repeats it until answered.
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
            return notification
        }

        /** Ring through the backup notification (1.0.4, A-2): the receiver could not start this service. */
        fun postBackupRing(context: Context, alarm: RingingAlarm) {
            ensureChannels(context)
            setBackup(alarm)
            postPlain(context, buildBackupNotification(context, alarm))
        }

        private fun postPlain(context: Context, notification: Notification) {
            try {
                context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
            } catch (e: RuntimeException) {
                // SecurityException without POST_NOTIFICATIONS on 13 and later: nothing more to try.
                Log.e(TAG, "could not post the backup ring", e)
            }
        }

        /** Takes the ring notification away (the backup's; the service's own goes with the service). */
        fun cancelRingNotification(context: Context) {
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        }

        /**
         * Stops whatever rings: the service, and the backup ring notification (1.0.4). Every Dismiss
         * comes here: the notification's, the ring screen's and the DISMISS_ALARM intent's.
         * `stopService` is allowed from the background on every version, where starting one is not.
         */
        fun dismiss(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
                .onFailure { Log.w(TAG, "could not stop the ringing service", it) }
            setBackup(null)
            cancelRingNotification(context)
        }
    }
}

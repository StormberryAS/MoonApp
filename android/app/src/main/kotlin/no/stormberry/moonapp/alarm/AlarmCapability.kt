package no.stormberry.moonapp.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.LunarEvent

/**
 * Storage and management service for MoonApp lunar alarms.
 *
 * For entry-level programmers:
 * SharedPreferences is Android's key-value store used to save simple settings across app restarts.
 * AlarmCapability saves the user's lunar alarm rules locally on disk, converts them between object state and text,
 * and calls AlarmPlanner to schedule exact system alarms.
 */
object AlarmCapability {

    private const val PREFS_NAME = "moonapp_alarms"
    private const val KEY_RULE_PREFIX = "rule_"

    // The label and the city name are user text and may contain a pipe, which would corrupt
    // the pipe-delimited record and silently drop the rule on the next load. Escape both.
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p")
    private const val KEY_CITY = "loc_city"
    private const val KEY_LAT = "loc_lat"
    private const val KEY_LON = "loc_lon"
    private const val KEY_TZ = "loc_tz"

    /**
     * The chosen location, which used to live only in a Compose `remember` and was therefore
     * reset to a hardcoded Oslo on every app start. An app whose whole purpose is
     * location-specific moon times must not forget where the user is. Fixed 2026-09-04.
     */
    data class SavedLocation(val city: String, val lat: Double, val lon: Double, val tz: String?)

    fun loadLocation(context: Context): SavedLocation {
        val p = getPrefs(context)
        return SavedLocation(
            p.getString(KEY_CITY, "Oslo") ?: "Oslo",
            java.lang.Double.longBitsToDouble(p.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(59.9139))),
            java.lang.Double.longBitsToDouble(p.getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(10.7522))),
            p.getString(KEY_TZ, "Europe/Oslo")
        )
    }

    /**
     * commit(), not apply(), here and in [saveRule] and [deleteRule].
     *
     * apply() hands the write to a background thread and returns. That is the right default
     * for a preference nobody would miss, and the wrong one for alarm rules: a process death
     * between the return and the flush loses the alarm silently, and the user finds out by not
     * being woken. Observed on 2026-09-05 when an `am force-stop` straight after a save left
     * no moonapp_alarms.xml on disk at all. These writes are user-initiated, infrequent, and
     * a few milliseconds of blocking on a file this small is not a cost worth trading for.
     */
    fun saveLocation(context: Context, city: String, lat: Double, lon: Double, tz: String?) {
        getPrefs(context).edit()
            .putString(KEY_TZ, tz)
            .putString(KEY_CITY, city)
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
            .commit()
    }

    private fun unesc(s: String) = s.replace("\\p", "|").replace("\\\\", "\\")

    /**
     * Alarm rules live in DEVICE-PROTECTED storage, not the default credential-encrypted
     * store. SystemEventReceiver is directBootAware and filters LOCKED_BOOT_COMPLETED, which
     * arrives BEFORE the first unlock: reading credential-encrypted preferences there throws,
     * so an unattended overnight reboot meant the alarm simply never rang. Same pattern as
     * SunApp's device-protected rule mirror. Fixed 2026-09-04.
     *
     * Device-protected storage is still app-private; it is readable before unlock rather than
     * readable by anything else. minSdk is 24 and createDeviceProtectedStorageContext is API 24.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        val dp = context.createDeviceProtectedStorageContext()
        // One-time move for anyone who already has rules in the old location.
        dp.moveSharedPreferencesFrom(context, PREFS_NAME)
        return dp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves a lunar alarm rule and updates its system schedule.
     */
    fun saveRule(context: Context, rule: MoonAlarmRule) {
        val prefs = getPrefs(context)
        val serialized = "${rule.id}|${esc(rule.label)}|${rule.event.name}|${rule.offsetMinutes}|" +
            "${esc(rule.cityName)}|${rule.latDeg}|${rule.lonDeg}|${rule.enabled}|${rule.vibrate}"
        prefs.edit().putString(KEY_RULE_PREFIX + rule.id, serialized).commit()

        // The whole plan, not this rule alone (1.0.4): a switched-off rule is cancelled there,
        // and the Android 12 backstop follows whichever alarm is now the nearest.
        AlarmPlanner.replan(context)
    }

    /**
     * Loads all configured lunar alarm rules from local storage.
     */
    fun loadRules(context: Context): List<MoonAlarmRule> {
        val prefs = getPrefs(context)
        val list = mutableListOf<MoonAlarmRule>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_RULE_PREFIX) && value is String) {
                val parts = value.split("|")
                if (parts.size >= 9) {
                    try {
                        val rule = MoonAlarmRule(
                            id = parts[0],
                            label = unesc(parts[1]),
                            event = LunarEvent.valueOf(parts[2]),
                            offsetMinutes = parts[3].toInt(),
                            cityName = unesc(parts[4]),
                            latDeg = parts[5].toDouble(),
                            lonDeg = parts[6].toDouble(),
                            enabled = parts[7].toBoolean(),
                            vibrate = parts[8].toBoolean()
                        )
                        list.add(rule)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    /**
     * Deletes a lunar alarm rule from local storage and cancels its schedule.
     */
    fun deleteRule(context: Context, ruleId: String) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_RULE_PREFIX + ruleId).remove(KEY_LAST_RUNG_PREFIX + ruleId).commit()
        AlarmPlanner.cancelAlarm(context, ruleId)
        AlarmPlanner.replan(context)
    }

    /**
     * Re-arms all enabled rules (e.g. after system reboot or timezone change).
     */
    fun rearmAllAlarms(context: Context) {
        AlarmPlanner.replan(context)
    }

    // ------------------------------------------------------------------ last ring (1.0.4)

    /**
     * The instant each rule last rang, in the same device-protected store as the rules, so the
     * receiver can read it before the first unlock. It is what stops one occurrence ringing twice:
     * the Android 12 backstop and the exact alarm both arriving (A-2), or a re-arm after the clock
     * was set back past a ring (the A-8 related case). See [FireRules].
     */
    private const val KEY_LAST_RUNG_PREFIX = "last_rung_"

    /** When [ruleId] last rang, or null; throws when the store cannot be read (the caller then rings). */
    fun lastRung(context: Context, ruleId: String): Long? {
        val p = getPrefs(context)
        return if (p.contains(KEY_LAST_RUNG_PREFIX + ruleId)) p.getLong(KEY_LAST_RUNG_PREFIX + ruleId, 0L) else null
    }

    /** Every rule's last ring. */
    fun lastRungAll(context: Context): Map<String, Long> =
        getPrefs(context).all.mapNotNull { (k, v) ->
            if (k.startsWith(KEY_LAST_RUNG_PREFIX) && v is Long) k.removePrefix(KEY_LAST_RUNG_PREFIX) to v else null
        }.toMap()

    /**
     * Drops the records that no longer count at [now] ([FireRules.rungCounts]: more than an hour
     * ahead of the clock, which was then set back a long way) and those of rules that no longer
     * exist. The re-plan runs this first, so the occurrence it then arms is not taken for one that
     * has rung when it arrives: after a large clock correction it rings again, as SunApp's.
     */
    fun pruneRung(context: Context, now: Long, ruleIds: Set<String>) {
        val p = getPrefs(context)
        val stale = p.all.keys.filter { k ->
            k.startsWith(KEY_LAST_RUNG_PREFIX) && run {
                val v = p.all[k]
                k.removePrefix(KEY_LAST_RUNG_PREFIX) !in ruleIds || (v is Long && !FireRules.rungCounts(v, now))
            }
        }
        if (stale.isEmpty()) return
        val e = p.edit()
        stale.forEach { e.remove(it) }
        e.commit()
    }

    /** Notes that [ruleId] rang its occurrence at [at]; never moves the record backwards. commit(), as for the rules. */
    fun markRung(context: Context, ruleId: String, at: Long) {
        val p = getPrefs(context)
        val before = if (p.contains(KEY_LAST_RUNG_PREFIX + ruleId)) p.getLong(KEY_LAST_RUNG_PREFIX + ruleId, 0L) else null
        if (before != null && before >= at) return
        p.edit().putLong(KEY_LAST_RUNG_PREFIX + ruleId, at).commit()
    }

    // ------------------------------------------------------------ what Android refuses (1.0.4)

    /** A platform setting that stops an alarm ringing in full, in order of severity. */
    enum class Gap { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN }

    /**
     * What the platform is refusing on this device right now; empty is the good case. Moved out
     * of the alarms screen in 1.0.4 (A-1), which checked only POST_NOTIFICATIONS on Android 13 and
     * later: notifications switched off for the app (any version) or the ringing channel turned
     * down went unnoticed, and then an alarm rings with nothing on screen.
     */
    fun gaps(context: Context): List<Gap> = gapsFrom(
        sdkInt = Build.VERSION.SDK_INT,
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false),
        canUseFullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            runCatching { context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }.getOrDefault(false),
        ringChannelImportance = channelImportance(context, AlarmService.CHANNEL_ID),
        backupChannelImportance = channelImportance(context, AlarmService.CHANNEL_BACKUP),
    )

    /**
     * The decision half of [gaps], with the platform reading passed in, so the version rules are
     * tested on the JVM. Notifications count as off when they are switched off for the app (which
     * on Android 13 and later includes POST_NOTIFICATIONS refused), or when either ringing channel
     * is blocked or below High (no heads-up and no full-screen ring screen), from Android 8.
     * Exact alarms can be switched off only on Android 12 and later, full-screen alarms only on 14
     * and later.
     */
    fun gapsFrom(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        canScheduleExactAlarms: Boolean,
        canUseFullScreenIntent: Boolean,
        ringChannelImportance: Int? = null,
        backupChannelImportance: Int? = null,
    ): List<Gap> = buildList {
        if (!notificationsEnabled || channelLowered(sdkInt, ringChannelImportance) || channelLowered(sdkInt, backupChannelImportance)) {
            add(Gap.NOTIFICATIONS)
        }
        if (sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms) add(Gap.EXACT_ALARMS)
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent) add(Gap.FULL_SCREEN)
    }

    /** Whether a channel of [importance] (null: none to read) is blocked or below High, from Android 8. */
    fun channelLowered(sdkInt: Int, importance: Int?): Boolean =
        sdkInt >= Build.VERSION_CODES.O && importance != null && importance < NotificationManagerCompat.IMPORTANCE_HIGH

    /**
     * The channel whose own settings page the notifications fix opens: the one ringing channel
     * that is turned down while notifications are on, or null for the app's notification page.
     */
    fun channelToFix(sdkInt: Int, notificationsEnabled: Boolean, ringChannelImportance: Int?, backupChannelImportance: Int?): String? {
        if (!notificationsEnabled) return null
        val ring = channelLowered(sdkInt, ringChannelImportance)
        val backup = channelLowered(sdkInt, backupChannelImportance)
        return when {
            ring && !backup -> AlarmService.CHANNEL_ID
            backup && !ring -> AlarmService.CHANNEL_BACKUP
            else -> null
        }
    }

    /** The importance of channel [id] now, or null when there is none to read. */
    fun channelImportance(context: Context, id: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching { NotificationManagerCompat.from(context).getNotificationChannelCompat(id)?.importance }.getOrNull()
    }

    /** The settings page that fixes the notifications gap: one channel's page, or the app's. */
    fun notificationSettingsIntent(context: Context): Intent {
        val channel = channelToFix(
            Build.VERSION.SDK_INT,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelImportance(context, AlarmService.CHANNEL_ID),
            channelImportance(context, AlarmService.CHANNEL_BACKUP),
        )
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channel != null ->
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

package no.stormberry.moonapp.alarm

import android.content.Context
import android.content.SharedPreferences
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

        if (rule.enabled) {
            AlarmPlanner.scheduleAlarm(context, rule)
        } else {
            AlarmPlanner.cancelAlarm(context, rule.id)
        }
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
        prefs.edit().remove(KEY_RULE_PREFIX + ruleId).commit()
        AlarmPlanner.cancelAlarm(context, ruleId)
    }

    /**
     * Re-arms all enabled rules (e.g. after system reboot or timezone change).
     */
    fun rearmAllAlarms(context: Context) {
        val rules = loadRules(context)
        for (rule in rules) {
            if (rule.enabled) {
                AlarmPlanner.scheduleAlarm(context, rule)
            }
        }
    }
}

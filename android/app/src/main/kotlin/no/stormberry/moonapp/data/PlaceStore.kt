package no.stormberry.moonapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The chosen place: the city's name, its coordinates and its IANA zone (1.0.8: moved here
 * unchanged from alarm/AlarmCapability).
 *
 * It moved because the home screen widgets read it too, in both editions. Kept in the alarm
 * package, the Google Play build (which has no alarms) carried the alarm class AlarmCapability in
 * its dex once the place had two readers, and the Play kit's bundle check refuses any class from
 * no/stormberry/moonapp/alarm/. The storage itself is exactly as before, so nothing a user saved
 * moves or is lost: the same preferences file, the same keys, the same device-protected store and
 * the same one-time move out of the old credential-encrypted location.
 *
 * Device-protected, not the default store, because the alarm receivers are directBootAware and
 * read the same file before the first unlock (see AlarmCapability). Device-protected storage is
 * still private to the app; it is readable before unlock, not readable by anything else.
 */
object PlaceStore {

    /** The file AlarmCapability keeps its rules in too. */
    private const val PREFS_NAME = "moonapp_alarms"
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

    private fun prefs(context: Context): SharedPreferences {
        val dp = context.createDeviceProtectedStorageContext()
        // One-time move for anyone who still has the file in the old location.
        dp.moveSharedPreferencesFrom(context, PREFS_NAME)
        return dp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadLocation(context: Context): SavedLocation {
        val p = prefs(context)
        return SavedLocation(
            p.getString(KEY_CITY, "Oslo") ?: "Oslo",
            java.lang.Double.longBitsToDouble(p.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(59.9139))),
            java.lang.Double.longBitsToDouble(p.getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(10.7522))),
            p.getString(KEY_TZ, "Europe/Oslo"),
        )
    }

    /**
     * commit(), not apply(): a process death between the return and the flush would lose the
     * place, and the widgets read it straight after this returns.
     */
    fun saveLocation(context: Context, city: String, lat: Double, lon: Double, tz: String?) {
        prefs(context).edit()
            .putString(KEY_TZ, tz)
            .putString(KEY_CITY, city)
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
            .commit()
    }
}

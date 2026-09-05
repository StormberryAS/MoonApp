package no.stormberry.moonapp.data

import android.content.Context

/**
 * The small preferences that are not alarm rules and not the saved location.
 *
 * Location and rules already live in [no.stormberry.moonapp.alarm.AlarmCapability], in
 * DEVICE-PROTECTED storage, because `SystemEventReceiver` is directBootAware and has to read
 * rules back before the first unlock. This class deliberately does NOT go there: the only
 * thing it holds is which first-run notice the user has acknowledged, which nothing reads
 * before unlock, and putting it in the credential-encrypted store keeps the direct-boot
 * surface as small as it can be.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Which revision of the first-run notice this install has seen. 0 on a fresh install,
     * which is why [no.stormberry.moonapp.ui.FIRST_RUN_NOTICE_VERSION] starts at 1.
     */
    var firstRunNoticeSeenVersion: Int
        get() = prefs.getInt(KEY_NOTICE_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_NOTICE_VERSION, value).apply()

    private companion object {
        const val PREFS_NAME = "moonapp_settings"
        const val KEY_NOTICE_VERSION = "first_run_notice_seen_version"
    }
}

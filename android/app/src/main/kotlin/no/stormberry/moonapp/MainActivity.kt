package no.stormberry.moonapp

import android.Manifest
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import no.stormberry.moonapp.alarm.AlarmPlanner
import no.stormberry.moonapp.alarm.AlarmService
import no.stormberry.moonapp.alarm.RingActivity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import no.stormberry.moonapp.ui.Moon
import no.stormberry.moonapp.ui.MoonApp
import no.stormberry.moonapp.ui.MoonAppTheme

/**
 * The activity, and nothing else. All 433 lines of UI that used to live here are now in the
 * `ui` package.
 *
 * ### enableEdgeToEdge and the insets padding
 *
 * `themes.xml` makes the status and navigation bars transparent, and targetSdk is 36, where
 * edge-to-edge is mandatory and cannot be opted out of. The window therefore extends behind
 * both system bars. v1.0.0 had no `enableEdgeToEdge` call and not one reference to
 * `WindowInsets` anywhere in the app, so nothing padded the content back: the header was drawn
 * underneath the status bar and the last alarm's toggle sat inside the home-gesture strip,
 * where a tap could be swallowed by the system gesture instead of toggling the alarm.
 *
 * `consumeWindowInsets` after `windowInsetsPadding` so that a child which asks for the same
 * insets does not pad for them a second time.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Set by [no.stormberry.moonapp.alarm.AlarmIntentActivity] answering SHOW_ALARMS. */
        const val EXTRA_OPEN_ALARMS = "no.stormberry.moonapp.OPEN_ALARMS"
        private const val TAG = "MoonMain"
    }

    // POST_NOTIFICATIONS was declared in the manifest but never requested, so on Android 13
    // and later the alarm notification was simply never shown. Combined with the blocked
    // background activity start in AlarmFireReceiver, that meant an alarm could ring with
    // nothing on screen to stop it. Fixed 2026-09-04. If it is denied, the alarms screen now
    // says so rather than showing the alarm as armed; see CapabilityBanner.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // SystemBarStyle.dark on both bars, not the default auto.
        //
        // `enableEdgeToEdge()` with no arguments picks the system-bar icon colour from
        // isSystemInDarkTheme(), so on a phone set to the light system theme it draws DARK
        // status icons. This app has no light variant, its background is #050810, and the
        // result was a clock and signal icons that were nearly invisible against it. The
        // `dark` factory means "dark background, therefore light icons", which is always
        // right here. Transparent scrims because themes.xml already makes both bars
        // transparent and the content is padded off them by windowInsetsPadding below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MoonAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Moon.Background,
                ) {
                    MoonApp(
                        startOnAlarms = intent?.getBooleanExtra(EXTRA_OPEN_ALARMS, false) == true,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .consumeWindowInsets(WindowInsets.systemBars),
                    )
                }
            }
        }

        // The channels exist from the first start (1.0.4), so the alarms screen can read how the
        // person left them and the system settings list both before any alarm has rung.
        AlarmService.ensureChannels(this)

        // 1.0.4, A-1: while this screen is in front, an alarm that rings brings the ring screen:
        // at once on resume when one already rings, and whenever one starts while it is open.
        // With notifications off or the ring channel turned down an alarm rings with nothing on
        // screen to stop it, so opening MoonApp has to be a way to stop it (SunApp 1.1.4).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { AlarmService.ringing.value }.collect { showRingScreenIfRinging() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        replanInBackground()
    }

    /**
     * 1.0.4, A-2: re-plan whenever the app comes to the front. On Android 12 and 12L, switching
     * exact alarms off cancels every exact alarm with no broadcast to say so (granting them back
     * does send one, which SystemEventReceiver handles), and until 1.0.4 nothing re-armed them
     * before the next reboot or clock change. Off the main thread: it reads the rules and computes
     * a lunar day per rule.
     */
    private fun replanInBackground() {
        val appContext = applicationContext
        Thread({
            try {
                AlarmPlanner.replan(appContext)
            } catch (e: RuntimeException) {
                Log.e(TAG, "re-planning on resume failed", e)
            }
        }, "moonapp-resume-replan").start()
    }

    private fun showRingScreenIfRinging() {
        if (AlarmService.current() == null || isFinishing || isDestroyed) return
        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not open the ring screen", e)
        }
    }

}

package no.stormberry.moonapp

import android.Manifest
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
    }
}

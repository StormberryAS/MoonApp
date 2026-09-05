package no.stormberry.moonapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.stormberry.moonapp.R
import no.stormberry.moonapp.ui.Moon

/**
 * The furniture around MoonApp's screens: the header mark, the footer lockup and the
 * background glow.
 *
 * Ported from SunApp's Chrome.kt so the two apps read as one product. v1.0.0 had none of it:
 * the times screen opened with a bare "MoonApp" text and no mark at all, which is the first
 * thing anyone comparing the two apps notices.
 */

/** Header: the mark, the name and the web app's tagline, near enough verbatim. */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            // Decorative: the title beside it is already the accessible name, so announcing
            // the mark as well would only slow a screen reader down.
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .drawBehind { drawLogoGlow() },
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MoonApp",
                style = MaterialTheme.typography.headlineSmall,
                color = Moon.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Moon times and lunar alarms, computed offline",
                style = MaterialTheme.typography.bodyMedium,
                color = Moon.TextSecondary,
            )
        }
    }
}

/** Footer: what the app does not do, and who made it. */
@Composable
fun AppFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Not "no permissions": the APK declares several, all of them for the alarm.
            // What is true, and is the claim the manifest defends, is that there is no
            // INTERNET permission at all, so the app cannot reach the network even if it
            // wanted to. Same wording as SunApp, for the same reason.
            text = "Alarm permissions only. No internet. No tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = Moon.TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.drawable.ic_stormberry_logo),
            // Decorative: the wordmark repeats the line above and nothing here is a link.
            contentDescription = null,
            // Full white would out-shout the app's own content this far down.
            alpha = 0.72f,
            // Height only: the drawable carries the lockup's proportions, so the width
            // follows from them and never has to be kept in sync.
            modifier = Modifier.height(24.dp),
        )
    }
}

/**
 * The web app's header glow and its cool lower wash, as two radial gradients.
 *
 * Deliberately not `Modifier.blur`, which needs API 31 and would leave the gradient flat for
 * every device between minSdk 24 and there. A soft radial gradient reaches the same look with
 * no API floor.
 *
 * The colours are MoonApp's, not SunApp's: style.css puts a cool
 * `0 0 40px rgba(123, 159, 212, 0.1)` behind this content, which is Moon.Blue, where SunApp
 * uses a warm sunrise orange. Keeping SunApp's gold here was how the icon ended up wrong.
 */
fun DrawScope.drawSkyGlow() {
    fun glow(colour: Color, centre: Offset, radius: Float, alpha: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colour.copy(alpha = alpha), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
    // Moonlight behind the header.
    glow(Moon.Blue, Offset(size.width * 0.5f, 0f), size.minDimension * 1.05f, 0.16f)
    glow(Moon.Silver, Offset(size.width * 0.18f, size.height * 0.04f), size.minDimension * 0.55f, 0.10f)
    // A violet wash towards the bottom, so a long scroll does not end in flat black.
    glow(Moon.Violet, Offset(size.width * 0.95f, size.height * 0.85f), size.minDimension * 0.9f, 0.09f)
}

/** The halo the web app puts behind the same mark with a CSS drop-shadow. */
private fun DrawScope.drawLogoGlow() {
    val radius = size.minDimension * 0.95f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Moon.Blue.copy(alpha = 0.34f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

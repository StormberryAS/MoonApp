package no.stormberry.moonapp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import no.stormberry.moonapp.BuildConfig
import no.stormberry.moonapp.R
import java.util.Locale
import no.stormberry.moonapp.ui.Moon

/**
 * The furniture around MoonApp's times screen: the hero at the top, the footer at the bottom
 * and the background glow.
 *
 * Since 1.0.5 the hero and the footer are CheckLink's, re-built natively and kept in MoonApp's
 * own colours. Before that they were SunApp's: a mark beside a left-aligned name, and a small
 * "Alarm permissions only" line with two links and the lockup. Marcos approved the wording on
 * 2026-10-23, with three changes to the first draft: the hero keeps the web's short tagline, the
 * credits are named in the footer itself, and the permissions line is back. Since 1.0.6 the
 * credits are only the projects and data the app's function is built on (SunCalc, GeoNames,
 * Kartverket); the icons, the typeface and the languages and libraries are in LICENSE and README.
 */

/**
 * Hero: the mark, the name and a tagline, all centred, as at the top of CheckLink.
 *
 * Replaces the 1.0.4 header, which set the mark to the left of a left-aligned name and tagline
 * and read as a toolbar rather than an opening. This one is CheckLink's hero re-built natively:
 * a 64 dp mark with its glow, the name large and centred, and a tagline in the secondary colour
 * under it, no wider than 34 rem (544 dp) so it still wraps on a tablet. The function cards
 * follow it, as on CheckLink.
 *
 * Sizes are CheckLink's (`.hero` in its style.css): 16 dp between the three, the name at
 * 1.9 rem and the tagline at 1.05 rem with a 1.55 line height. All in sp, so Android's font
 * size setting scales them as the browser's does there.
 */
@Composable
fun AppHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            // Decorative: the name under it is already the accessible name, so announcing the
            // mark as well would only slow a screen reader down.
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .drawBehind { drawLogoGlow() },
        )
        Text(
            text = APP_NAME,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Moon.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            // The web hero sets this in capitals with CSS (text-transform: uppercase), so it is
            // set the same way here; the constant stays the page's own text.
            text = HERO_TAGLINE.uppercase(Locale.ROOT),
            modifier = Modifier.widthIn(max = 544.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.2.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.06.em,
            ),
            color = Moon.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

const val APP_NAME = "MoonApp"

/**
 * The tagline of the moon.stormberry.as hero, word for word: `<p class="tagline">` in index.html,
 * where the line break is a `<br>`. Marcos, 2026-10-23: keep the short tagline, consistent with
 * the web; where the app and the page differ, the page wins. HeroTaglineTest reads index.html
 * and fails if the two drift apart. (1.0.4 said "Moon times and lunar alarms, computed
 * offline"; the 1.0.5 draft was a long sentence. Neither is used any more.)
 */
const val HERO_TAGLINE = "MOONRISE \u00B7 TRANSIT \u00B7 MOONSET\nanywhere on Earth"

/**
 * Footer, as CheckLink's: the Stormberry wordmark and tagline, what runs on the device, a
 * divider, the icon row, the bold privacy line, then the small print.
 *
 * Laid out the way CheckLink's `.site-footer` lays itself out on a phone: full width on the
 * page's darkest colour under a hairline, 24 dp of side padding, the brand block above the
 * "runs on your device" block, then a centred column under a second hairline.
 *
 * ### Opening a link from an app with no INTERNET permission
 *
 * The manifest does not merely omit INTERNET, it removes it:
 *
 *     <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
 *
 * so this process cannot open a socket. That is not in tension with the links below.
 * [openLink] fires an ACTION_VIEW intent and the BROWSER (or the Nostr client) does the
 * fetching, in its own process with its own permissions. Nothing here needs INTERNET and
 * nothing here should ever be "fixed" by adding it: the whole privacy claim rests on that
 * permission being absent, and `android/expected-permissions.txt` plus the CI diff against the
 * built APK is what keeps the claim honest.
 */
@Composable
fun AppFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val open: (String) -> Unit = remember(context) { { url: String -> openLink(context, url); Unit } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Moon.Background)
            .drawBehind {
                drawLine(Moon.Border, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The two blocks of .footer-inner, which wrap onto their own rows on a phone.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append("Stormberry")
                        withStyle(SpanStyle(color = Moon.Blue)) { append(".") }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Moon.TextPrimary,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Stormberry AS website"
                            role = Role.Button
                        }
                        .clickable(role = Role.Button) { open(FooterLinks.SITE) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = FooterText.TAGLINE,
                    style = footerStyle(15.2.sp, 23.sp),
                    color = Moon.TextSoft,
                )
            }

            Text(
                text = buildAnnotatedString {
                    append("Runs entirely on your device: our own code, ")
                    footerLink("SunCalc", FooterLinks.SUNCALC, open)
                    append("'s formulae (")
                    footerLink(FooterText.LICENCE_BSD, FooterLinks.SUNCALC_LICENCE, open)
                    append(") and city data from ")
                    footerLink("GeoNames", FooterLinks.GEONAMES, open)
                    append(" and ")
                    footerLink("Kartverket", FooterLinks.KARTVERKET, open)
                    append(" (")
                    footerLink(FooterText.LICENCE_CC_BY, FooterLinks.CC_BY, open)
                    append("). Nothing is sent.")
                },
                modifier = Modifier.widthIn(max = 320.dp),
                style = footerStyle(14.1.sp, 22.sp),
                color = Moon.TextSoft,
            )
        }

        Spacer(Modifier.height(48.dp))

        // .footer-bottom: a hairline, then a centred column.
        HorizontalDivider(color = Moon.BorderSoft)
        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                for (icon in FooterIcons) {
                    SocialIcon(icon, onClick = { open(icon.url) })
                }
            }

            Text(
                text = if (BuildConfig.PLAY_BUILD) FooterText.OFFLINE_PLAY else FooterText.OFFLINE,
                style = footerStyle(14.4.sp, 23.sp, FontWeight.SemiBold),
                color = Moon.TextSoft,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (BuildConfig.PLAY_BUILD) FooterText.PERMISSIONS_PLAY else FooterText.PERMISSIONS,
                style = footerStyle(13.6.sp, 22.sp),
                color = Moon.TextSoft,
                textAlign = TextAlign.Center,
            )

            Text(
                text = buildAnnotatedString {
                    append("\u00A9 2026 Stormberry AS \u00B7 ")
                    footerLink(FooterText.ORG_NR, FooterLinks.ORG_NR, open)
                    append(" \u00B7 Ask\u00F8y, Norway \u00B7 ")
                    footerLink("Privacy", FooterLinks.PRIVACY, open)
                    append(" \u00B7 ")
                    footerLink("Disclaimer", FooterLinks.DISCLAIMER, open)
                },
                style = footerStyle(13.6.sp, 22.sp),
                color = Moon.TextMutedReadable,
                textAlign = TextAlign.Center,
            )
            Text(
                text = FooterText.PRIVACY_FIRST,
                style = footerStyle(13.6.sp, 22.sp),
                color = Moon.TextMutedReadable,
                textAlign = TextAlign.Center,
            )
            Text(
                text = FooterText.CITY_LIST_DATED,
                style = footerStyle(13.6.sp, 22.sp),
                color = Moon.TextMutedReadable,
                textAlign = TextAlign.Center,
            )
            Text(
                text = FooterText.AI,
                style = footerStyle(13.6.sp, 22.sp),
                color = Moon.TextMutedReadable,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun footerStyle(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = MaterialTheme.typography.bodySmall.copy(
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = weight,
)

/**
 * A link in running footer text: the app's blue AND underlined, because colour alone is not an
 * accessible affordance for a link (CheckLink's footer does the same). A real link annotation,
 * so TalkBack lists it among the text's links.
 *
 * Spaces inside [label] are no-break spaces, which is how the web footer's `white-space: nowrap`
 * is kept: the org.nr. does not break inside itself.
 */
private fun AnnotatedString.Builder.footerLink(
    label: String,
    url: String,
    open: (String) -> Unit,
) {
    withLink(
        LinkAnnotation.Clickable(
            tag = url,
            styles = TextLinkStyles(
                SpanStyle(color = Moon.Blue, textDecoration = TextDecoration.Underline),
            ),
            linkInteractionListener = { open(url) },
        ),
    ) { append(label) }
}

/** One mark of the icon row: 24 dp drawn, 48 dp to touch, named for a screen reader. */
@Composable
private fun SocialIcon(icon: FooterIcon, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${icon.label}", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon.drawable),
            contentDescription = icon.label,
            tint = Moon.TextMutedReadable,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** What the icon row shows, in CheckLink's order: LinkedIn, GitHub, Nostr, Tor. */
private class FooterIcon(val label: String, val url: String, @param:DrawableRes val drawable: Int)

private val FooterIcons = listOf(
    FooterIcon("LinkedIn", FooterLinks.LINKEDIN, R.drawable.ic_social_linkedin),
    FooterIcon("GitHub", FooterLinks.GITHUB, R.drawable.ic_social_github),
    FooterIcon("Nostr", FooterLinks.NOSTR, R.drawable.ic_social_nostr),
    FooterIcon("Tor onion site", FooterLinks.ONION, R.drawable.ic_social_onion),
)

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

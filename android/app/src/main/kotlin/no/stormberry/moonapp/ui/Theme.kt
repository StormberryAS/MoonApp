package no.stormberry.moonapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * MoonApp's palette, lifted verbatim from the web app's style.css custom properties so the
 * APK and moon.stormberry.as look like one product.
 *
 * This file exists because the app previously had no theme at all. `MoonAppTheme` was
 * literally `MaterialTheme(content = content)`, and Material 3 backs its colour scheme with
 * `staticCompositionLocalOf { lightColorScheme() }`. Every component therefore resolved to the
 * baseline LIGHT palette: `OutlinedTextField` took `onSurface` = #1D1B20 for its input colour
 * and painted it on a #1E293B card, about 1.05:1 contrast, so the city name and the two
 * coordinate fields were invisible on the app's only location control.
 *
 * Dark-only by design, like SunApp. isSystemInDarkTheme() is deliberately not consulted
 * because a light variant of this palette does not exist.
 */
object Moon {
    /** --bg-base */
    val Background = Color(0xFF050810)
    /** --bg-surface */
    val Surface = Color(0xFF090D1E)
    /** --bg-card, rgba(9, 13, 32, 0.75) */
    val Card = Color(0xBF090D20)
    /** --bg-card-hover, rgba(14, 20, 48, 0.88) */
    val CardHover = Color(0xE00E1430)
    /** --border, rgba(255, 255, 255, 0.07) */
    val Border = Color(0x12FFFFFF)
    /** --border-active, rgba(200, 216, 240, 0.30) */
    val BorderActive = Color(0x4DC8D8F0)

    /** --text-primary */
    val TextPrimary = Color(0xFFE8EEFF)
    /** --text-secondary */
    val TextSecondary = Color(0xFF7B8FC4)
    /** --text-muted */
    val TextMuted = Color(0xFF3A4570)

    /** --accent-silver, the lit-limb accent and the app's primary */
    val Silver = Color(0xFFC8D8F0)
    /** --accent-blue */
    val Blue = Color(0xFF7B9FD4)
    /** --accent-indigo */
    val Indigo = Color(0xFF5B72B8)
    /** --accent-violet, used for lunar transit */
    val Violet = Color(0xFF9B7FD4)
    /** --accent-teal, used for moonset */
    val Teal = Color(0xFF4FC3F7)
    /** --accent-new, the new-moon grey */
    val NewMoon = Color(0xFFB0B8D4)
    /** No --accent-error in style.css; the shared Stormberry rose, as SunApp uses. */
    val Error = Color(0xFFFF6B6B)
}

private val MoonColors = darkColorScheme(
    primary = Moon.Silver,
    onPrimary = Moon.Background,
    secondary = Moon.Blue,
    onSecondary = Moon.Background,
    tertiary = Moon.Violet,
    background = Moon.Background,
    onBackground = Moon.TextPrimary,
    surface = Moon.Surface,
    onSurface = Moon.TextPrimary,
    surfaceVariant = Moon.Card,
    onSurfaceVariant = Moon.TextSecondary,
    outline = Moon.Border,
    error = Moon.Error,
)

private val MoonTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Field colours for every OutlinedTextField in the app, mirroring SunApp's `sunFieldColours()`.
 *
 * Belt and braces next to the colour scheme above: the scheme alone now fixes the contrast,
 * but naming the text colour at the call site means a future theme edit cannot quietly make
 * typed input unreadable again.
 */
@Composable
fun moonFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Moon.TextPrimary,
    unfocusedTextColor = Moon.TextPrimary,
    disabledTextColor = Moon.TextMuted,
    focusedBorderColor = Moon.BorderActive,
    unfocusedBorderColor = Moon.Border,
    focusedLabelColor = Moon.Silver,
    unfocusedLabelColor = Moon.TextSecondary,
    cursorColor = Moon.Silver,
    focusedContainerColor = Moon.Surface,
    unfocusedContainerColor = Moon.Surface,
)

@Composable
fun MoonAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoonColors,
        typography = MoonTypography,
        content = content,
    )
}

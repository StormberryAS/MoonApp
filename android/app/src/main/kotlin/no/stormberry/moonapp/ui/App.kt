package no.stormberry.moonapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.stormberry.moonapp.alarm.AlarmCapability

/**
 * The app's composable root: two screens and the bar that swaps them.
 *
 * Deliberately thin, like SunApp's. It owns the one piece of state both screens need, the
 * chosen place, and nothing else.
 *
 * ### Why the place lives here rather than in the times screen
 *
 * An alarm is anchored to coordinates, which are copied into each [no.stormberry.moonapp.alarm.model.MoonAlarmRule]
 * when it is created. v1.0.0 called `rearmAllAlarms` after a location change with a comment
 * saying alarms follow the location, but re-arming re-reads each rule's OWN stored coordinates,
 * so existing alarms kept firing on the old place while the header showed the new one. Holding
 * the place at the root is what lets [onPlaceChange] rewrite the stored rules as well as
 * re-arm them, which is what that comment always claimed.
 *
 * ### Why a bottom bar rather than a top one
 *
 * Same reasoning as SunApp: the times screen owns the header and it scrolls. Navigation above
 * it would either duplicate that header or push it off the top of a phone; below leaves both
 * screens as designed and puts the control where a thumb already is.
 */
@Composable
fun MoonApp(startOnAlarms: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { no.stormberry.moonapp.data.Settings(context) }

    val saved = remember { AlarmCapability.loadLocation(context) }
    var place by remember {
        mutableStateOf(ChosenPlace(saved.city, saved.lat, saved.lon, saved.tz))
    }
    var tab by remember { mutableStateOf(if (startOnAlarms) MoonTab.ALARMS else MoonTab.TIMES) }

    var showNotice by remember {
        mutableStateOf(shouldShowFirstRunNotice(settings.firstRunNoticeSeenVersion))
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (tab) {
            MoonTab.TIMES -> MoonTimesScreen(
                place = place,
                onPlaceChange = { chosen ->
                    place = chosen
                    // Move every stored rule to the new place and re-arm it. Without this the
                    // list still reads "City: Oslo" on alarms the user believes they moved.
                    for (rule in AlarmCapability.loadRules(context)) {
                        AlarmCapability.saveRule(
                            context,
                            rule.copy(
                                cityName = chosen.label,
                                latDeg = chosen.latDeg,
                                lonDeg = chosen.lonDeg,
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )

            MoonTab.ALARMS -> AlarmsRoot(
                defaultPlace = place,
                onRulesChanged = { },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = Moon.Border)
        MoonTabBar(tab = tab, onSelect = { tab = it })

        if (showNotice) {
            FirstRunNotice(onDismiss = {
                settings.firstRunNoticeSeenVersion = FIRST_RUN_NOTICE_VERSION
                showNotice = false
            })
        }
    }
}

/** The two things MoonApp does. */
enum class MoonTab { TIMES, ALARMS }

@Composable
private fun MoonTabBar(tab: MoonTab, onSelect: (MoonTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Moon.Background)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabChip("Moon times", tab == MoonTab.TIMES, { onSelect(MoonTab.TIMES) }, Modifier.weight(1f))
        TabChip("Alarms", tab == MoonTab.ALARMS, { onSelect(MoonTab.ALARMS) }, Modifier.weight(1f))
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = { if (!selected) onSelect() },
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Moon.Surface,
            labelColor = Moon.TextSecondary,
            selectedContainerColor = Moon.CardHover,
            selectedLabelColor = Moon.Silver,
        ),
        border = null,
        modifier = modifier,
    )
}

package no.stormberry.moonapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import no.stormberry.moonapp.alarm.AlarmCapability
import no.stormberry.moonapp.ui.components.AppFooter
import no.stormberry.moonapp.ui.components.AppHeader
import no.stormberry.moonapp.ui.components.drawSkyGlow
import no.stormberry.moonapp.lunar.LunarDayKind
import no.stormberry.moonapp.lunar.LunarEvent
import no.stormberry.moonapp.lunar.MoonCalc
import no.stormberry.moonapp.lunar.MoonPhase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The moon-times screen: what the moon is doing, where you are, on the day you pick.
 *
 * ### Everything is sampled at 12:00 UTC of the selected date
 *
 * That is what the web app does (`app.js:315`), and the two surfaces are supposed to agree.
 * The old card sampled illumination at `Instant.now()`, so the figure drifted through the day
 * and read a point below the website on roughly half of all days, and truncated with `.toInt()`
 * where the web rounds. [MoonPhase.noonUtc] is the single place that decision lives.
 *
 * ### Why the polar cases are three messages and not one
 *
 * `MoonCalc.dayKind` has existed since the port and was never called. Both "the moon is up all
 * day" and "the moon never comes up" printed the same sentence, "does not occur today", which
 * at Bergen and further north happens several days a month and tells the user the opposite of
 * what they need to know. A transit time is also suppressed on an always-down day: the old card
 * printed a peak altitude for a moon that is below the horizon for all 24 hours, contradicting
 * the two rows above it.
 */
@Composable
fun MoonTimesScreen(
    place: ChosenPlace,
    onPlaceChange: (ChosenPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Hoisted out of CityPicker so scrolling the card off screen cannot discard half-typed
    // input. rememberSaveable so a rotation does not either.
    var query by rememberSaveable { mutableStateOf("") }
    var latText by rememberSaveable { mutableStateOf("") }
    var lonText by rememberSaveable { mutableStateOf("") }

    // The date is NOT persisted. Opening the app tomorrow should show tomorrow, not the day
    // you last happened to be curious about. Same rule as SunApp.
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val zone = remember(place.zoneId) {
        runCatching { ZoneId.of(place.zoneId ?: "") }.getOrElse { ZoneId.systemDefault() }
    }
    val noon = remember(date) { MoonPhase.noonUtc(date) }
    // The place's zone, not UTC. This is the whole fix for the app disagreeing with
    // moon.stormberry.as: see the KDoc on MoonCalc.times.
    val times = remember(place.latDeg, place.lonDeg, date, zone) {
        MoonCalc.times(date, place.latDeg, place.lonDeg, zone)
    }
    val kind = remember(place.latDeg, place.lonDeg, date, zone) {
        MoonCalc.dayKind(date, place.latDeg, place.lonDeg, zone)
    }
    val illum = remember(noon) { MoonCalc.illumination(noon) }
    val position = remember(noon, place.latDeg, place.lonDeg) {
        MoonCalc.position(noon, place.latDeg, place.lonDeg)
    }
    val lunations = remember(date) { MoonPhase.nextLunations(date) }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawSkyGlow() },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AppHeader() }

        item {
            DateBar(
                date = date,
                label = date.format(dateFmt),
                onPrev = { date = date.minusDays(1) },
                onNext = { date = date.plusDays(1) },
                onToday = { date = LocalDate.now() },
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Moon.Card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        MoonPhase.name(illum.phase),
                        style = MaterialTheme.typography.titleMedium,
                        color = Moon.Silver,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Math.round, matching app.js:357. The old card truncated with .toInt().
                    StatRow("Illumination", "${Math.round(illum.fraction * 100)}%")
                    StatRow("Distance", "${"%,d".format(Math.round(position.distanceKm))} km")
                    StatRow("Next new moon", lunations.nextNew?.format(dateFmt) ?: "Not found")
                    StatRow("Next full moon", lunations.nextFull?.format(dateFmt) ?: "Not found")

                    Spacer(Modifier.height(12.dp))
                    Text(
                        place.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Moon.TextPrimary,
                    )
                    Text(
                        MoonPhase.formatCoordinates(place.latDeg, place.lonDeg),
                        style = MaterialTheme.typography.bodySmall,
                        color = Moon.TextSecondary,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Moon.Card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    when (kind) {
                        LunarDayKind.ALWAYS_UP -> PolarNotice(
                            "The moon is above the horizon all day",
                            "It neither rises nor sets on this date at this latitude.",
                            Moon.Silver,
                        )
                        LunarDayKind.ALWAYS_DOWN -> PolarNotice(
                            "The moon stays below the horizon all day",
                            "It neither rises nor sets on this date at this latitude.",
                            Moon.NewMoon,
                        )
                        LunarDayKind.NORMAL -> Unit
                    }

                    if (kind != LunarDayKind.NORMAL) Spacer(Modifier.height(12.dp))

                    fun show(i: Instant?): String =
                        i?.atZone(zone)?.format(timeFmt) ?: "—"

                    TimeRow("Moonrise", show(times[LunarEvent.MOONRISE]), Moon.Blue)
                    // Suppressed when the moon is below the horizon for the whole day: a peak
                    // altitude for a moon that never comes up contradicts the rows around it.
                    if (kind != LunarDayKind.ALWAYS_DOWN) {
                        TimeRow("Transit", show(times[LunarEvent.LUNAR_TRANSIT]), Moon.Violet)
                    }
                    TimeRow("Moonset", show(times[LunarEvent.MOONSET]), Moon.Teal)

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Times shown in ${zone.id}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Moon.TextMuted,
                    )
                }
            }
        }

        item {
            CityPicker(
                query = query,
                onQueryChange = { query = it },
                latText = latText,
                onLatTextChange = { latText = it },
                lonText = lonText,
                onLonTextChange = { lonText = it },
                onPlaceChosen = { chosen ->
                    AlarmCapability.saveLocation(
                        context, chosen.label, chosen.latDeg, chosen.lonDeg, chosen.zoneId
                    )
                    // Every armed alarm is anchored to a location, so moving the location has
                    // to re-arm them or they keep firing on the old one. The old code called
                    // rearmAllAlarms but each stored rule kept its OWN latDeg/lonDeg, so the
                    // call was a no-op for existing rules; App.kt now rewrites them.
                    onPlaceChange(chosen)
                },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            AppFooter()
        }
    }
}

@Composable
private fun DateBar(
    date: LocalDate,
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev) { Text("‹", color = Moon.Silver) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Moon.TextPrimary,
                )
                if (date != LocalDate.now()) {
                    TextButton(onClick = onToday) {
                        Text("Back to today", color = Moon.TextSecondary)
                    }
                }
            }
            TextButton(onClick = onNext) { Text("›", color = Moon.Silver) }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Moon.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Moon.TextPrimary)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TimeRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = accent)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Moon.TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PolarNotice(title: String, body: String, accent: Color) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Moon.TextSecondary)
    }
}

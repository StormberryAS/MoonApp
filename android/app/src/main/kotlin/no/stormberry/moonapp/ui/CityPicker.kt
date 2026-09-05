package no.stormberry.moonapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import no.stormberry.moonapp.cities.CityAssets
import no.stormberry.moonapp.cities.CitySearch
import no.stormberry.moonapp.lunar.MoonPhase

/**
 * A resolved place, exactly what the times screen and a new alarm both need.
 *
 * [zoneId] is a plain IANA id rather than a `ZoneId` because it is written to preferences and
 * an id that no longer resolves must not throw on the way out of the store. It is nullable
 * only in the sense that resolution can fail; the picker itself always supplies one now.
 */
data class ChosenPlace(
    val label: String,
    val latDeg: Double,
    val lonDeg: Double,
    val zoneId: String?,
)

/**
 * Parses a typed latitude or longitude, ported from SunApp so the two apps accept exactly the
 * same input. Handles the Unicode minus a phone keyboard may produce, the COMMA decimal
 * separator a Norwegian keyboard produces by default, and a trailing degree sign. NaN and the
 * infinities parse happily as Doubles and would poison every downstream comparison, so they
 * are rejected here rather than checked for later.
 */
fun parseCoordinate(raw: String): Double? {
    val cleaned = raw.trim()
        .replace('−', '-')
        .replace(',', '.')
        .removeSuffix("°")
        .trim()
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (value.isFinite()) value else null
}

/**
 * The location control: search the bundled catalogue, or type coordinates.
 *
 * ### Why the text state is hoisted
 *
 * Every field's text is a parameter rather than a `remember` inside this composable. The card
 * sits in a scrolling list, and a `remember` here was disposed the moment the card left the
 * viewport, so a half-typed latitude or an in-progress city search vanished if the user
 * scrolled down to the alarms and back. With two or three alarms present that happened on an
 * ordinary phone screen.
 *
 * ### Why typed coordinates still get a timezone
 *
 * `CitySearch.nearestTimezone` has existed since the port and was never called. Manual
 * coordinates therefore stored a null zone and fell back to the phone's, so typing Sydney's
 * coordinates on a Norwegian phone printed Sydney's moon times in Europe/Oslo, ten hours out,
 * with a footer that named the wrong zone. It is the same symptom the city path was fixed for
 * on 2026-09-04; only half the fix landed.
 */
@Composable
fun CityPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    latText: String,
    onLatTextChange: (String) -> Unit,
    lonText: String,
    onLonTextChange: (String) -> Unit,
    onPlaceChosen: (ChosenPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val table = remember { runCatching { CityAssets.load(context) }.getOrNull() }
    val matches = remember(query, table) {
        if (table == null || query.length < 2) emptyList()
        else CitySearch.search(table, query, limit = 6)
    }
    var coordError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Change location",
                style = MaterialTheme.typography.titleMedium,
                color = Moon.Silver,
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search 25,007 cities") },
                colors = moonFieldColours(),
            )

            for (c in matches) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        onQueryChange("")
                        onPlaceChosen(ChosenPlace("${c.name}, ${c.country}", c.lat, c.lon, c.tz))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moon.CardHover),
                ) {
                    Text("${c.name}, ${c.country}", color = Moon.TextPrimary)
                }
            }

            if (table == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "City catalogue unavailable. Alarms still work at the saved location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Moon.TextSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Or type coordinates",
                style = MaterialTheme.typography.bodyMedium,
                color = Moon.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))

            // KeyboardType.Decimal, not the default text keyboard. Entering a coordinate
            // means digits, a minus and a decimal separator; on a QWERTY layout that is a
            // page switch per character. Decimal offers all three directly and still accepts
            // the comma a Norwegian layout produces, which parseCoordinate normalises.
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { onLatTextChange(it); coordError = null },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Latitude") },
                    colors = moonFieldColours(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { onLonTextChange(it); coordError = null },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Longitude") },
                    colors = moonFieldColours(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            coordError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Moon.Error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val la = parseCoordinate(latText)
                    val lo = parseCoordinate(lonText)
                    when {
                        la == null || lo == null ->
                            coordError = "Enter two numbers, for example 60,39 and 5,32."
                        la < -90.0 || la > 90.0 ->
                            coordError = "Latitude must be between -90 and 90."
                        lo < -180.0 || lo > 180.0 ->
                            coordError = "Longitude must be between -180 and 180."
                        else -> {
                            coordError = null
                            // The nearest catalogue city's zone, not the phone's. See the
                            // KDoc above: this call is the whole fix for manual coordinates
                            // rendering in the wrong timezone.
                            val tz = table?.let { CitySearch.nearestTimezone(it, la, lo) }
                            onPlaceChosen(
                                ChosenPlace(MoonPhase.formatCoordinates(la, lo), la, lo, tz)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moon.Indigo),
            ) {
                Text("Use these coordinates", color = Moon.TextPrimary)
            }
        }
    }
}

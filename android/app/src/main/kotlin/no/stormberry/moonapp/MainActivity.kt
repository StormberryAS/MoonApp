package no.stormberry.moonapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.stormberry.moonapp.alarm.AlarmCapability
import no.stormberry.moonapp.cities.CityAssets
import no.stormberry.moonapp.cities.CitySearch
import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.LunarEvent
import no.stormberry.moonapp.lunar.MoonCalc
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.UUID

/**
 * Main Activity for MoonApp Android application.
 *
 * For entry-level programmers:
 * ComponentActivity is the base class for activities using Jetpack Compose UI.
 * setContent defines the UI layout tree declaratively using Composable functions.
 */
class MainActivity : ComponentActivity() {

    // POST_NOTIFICATIONS was declared in the manifest but never requested, so on Android 13
    // and later the alarm notification was simply never shown. Combined with the blocked
    // background activity start in AlarmFireReceiver, that meant an alarm could ring with
    // nothing on screen to stop it. Fixed 2026-09-04.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    color = Color(0xFF0F172A) // Sovereign Deep Night Theme
                ) {
                    MoonAppMainScreen()
                }
            }
        }
    }
}


/**
 * Parses a typed latitude or longitude, ported from SunApp's SunTimesScreen so the two apps
 * accept exactly the same input. Handles the Unicode minus a phone keyboard may produce, the
 * COMMA decimal separator a Norwegian keyboard produces by default, and a trailing degree
 * sign. NaN and the infinities parse happily as Doubles and would poison every downstream
 * comparison, so they are rejected here rather than checked for later.
 */
internal fun parseCoordinate(raw: String): Double? {
    val cleaned = raw.trim()
        .replace('\u2212', '-')
        .replace(',', '.')
        .removeSuffix("\u00B0")
        .trim()
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (value.isFinite()) value else null
}

@Composable
fun MoonAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
fun MoonAppMainScreen() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(AlarmCapability.loadRules(context)) }
    // Restored from storage rather than hardcoded: the location used to reset to Oslo on
    // every start, which is a poor look for an app that computes location-specific times.
    val saved = remember { AlarmCapability.loadLocation(context) }
    var selectedCity by remember { mutableStateOf(saved.city) }
    var latDeg by remember { mutableStateOf(saved.lat) }
    var lonDeg by remember { mutableStateOf(saved.lon) }
    var savedTz by remember { mutableStateOf(saved.tz) }

    // Keyed on the coordinates AND the date. Keying on the city name alone meant a manual
    // lat/lon entry never recomputed, and nothing keyed on the date at all, so the figures
    // went stale across midnight and the illumination was frozen for the whole process.
    val today = LocalDate.now()
    val todayTimes = remember(latDeg, lonDeg, today) { MoonCalc.times(today, latDeg, lonDeg) }
    val todayIllum = remember(today) { MoonCalc.illumination(Instant.now()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header Title
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "🌕 MoonApp",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Moon times and lunar alarms, computed offline",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        item {
            // Current Moon Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Lunar Phase",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Illumination: ${(todayIllum.fraction * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Location: $selectedCity ($latDeg° N, $lonDeg° E)",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )

                    // MOONRISE, TRANSIT AND MOONSET. These were computed and then thrown
                    // away: the app displayed only an illumination percentage, which for an
                    // app called MoonApp is the core function missing. Found by running the
                    // build on an emulator 2026-09-04; no unit test could have caught it.
                    //
                    // Times are shown in the DEVICE timezone and labelled as such. At high
                    // latitude the moon can stay up or down all day, in which case MoonCalc
                    // returns null and we say so rather than inventing a time.
                                    // THE SELECTED CITY'S ZONE, not the phone's. Showing Sydney's moon
                    // times on a Norwegian phone in Europe/Oslo put moonset eight hours
                    // before the moonrise above it, and made the store claim that the app
                    // "handles the timezone properly" false. SunApp resolves the place's
                    // IANA id and only falls back to the device zone for manual
                    // coordinates; do the same. Fixed 2026-09-04.
                    val zone = remember(savedTz) {
                        runCatching { ZoneId.of(savedTz ?: "") }.getOrElse { ZoneId.systemDefault() }
                    }
                    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
                    fun show(i: Instant?): String =
                        i?.atZone(zone)?.format(fmt) ?: "does not occur today"

                    Spacer(modifier = Modifier.height(12.dp))
                    for ((label, value) in listOf(
                        "Moonrise" to todayTimes[LunarEvent.MOONRISE],
                        "Transit" to todayTimes[LunarEvent.LUNAR_TRANSIT],
                        "Moonset" to todayTimes[LunarEvent.MOONSET],
                    )) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = Color(0xFF94A3B8), fontSize = 15.sp)
                            Text(text = show(value), color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = "Times shown in ${zone.id}.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // CITY PICKER. The 25,007-city catalogue is the same data the website uses, parsed
        // once per process from assets. Before 2026-09-04 the app was hardwired to Oslo with
        // no way to change it, while zapstore.yaml already advertised a bundled catalogue.
        item {
            var query by remember { mutableStateOf("") }
            val table = remember { runCatching { CityAssets.load(context) }.getOrNull() }
            val matches = remember(query, table) {
                if (table == null || query.length < 2) emptyList()
                else CitySearch.search(table, query, limit = 6)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Change location",
                        color = Color(0xFF38BDF8),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search 25,007 cities") }
                    )
                    // MANUAL COORDINATES, matching SunApp and the website. The web version has
                    // three input methods; without this the APK had only one, and a place that
                    // is not a city in the catalogue was simply unreachable.
                    Spacer(modifier = Modifier.height(12.dp))
                    var latText by remember { mutableStateOf("") }
                    var lonText by remember { mutableStateOf("") }
                    var coordError by remember { mutableStateOf<String?>(null) }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it; coordError = null },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("Latitude") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it; coordError = null },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("Longitude") }
                        )
                    }
                    coordError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = it, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    val label = "%.4f, %.4f".format(la, lo)
                                    selectedCity = label
                                    latDeg = la
                                    lonDeg = lo
                                    coordError = null
                                    // manual coordinates carry no zone; fall back to the device
                                    savedTz = null
                                    AlarmCapability.saveLocation(context, label, la, lo, null)
                                    AlarmCapability.rearmAllAlarms(context)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Use these coordinates", color = Color.White, fontSize = 14.sp)
                    }

                    if (table == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "City catalogue unavailable. Alarms still work at the saved location.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    for (c in matches) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                selectedCity = c.name
                                latDeg = c.lat
                                lonDeg = c.lon
                                query = ""
                                savedTz = c.tz
                                AlarmCapability.saveLocation(context, c.name, c.lat, c.lon, c.tz)
                                // Every armed alarm is anchored to a location, so moving the
                                // location has to re-arm them or they keep firing on the old one.
                                AlarmCapability.rearmAllAlarms(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("${c.name}, ${c.country}", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        item {
            // Lunar Alarm Control Header
            Text(
                text = "Lunar alarms",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            // Quick Add Alarm Button
            Button(
                onClick = {
                    val newRule = MoonAlarmRule(
                        id = UUID.randomUUID().toString(),
                        label = "Moonrise Alert",
                        event = LunarEvent.MOONRISE,
                        offsetMinutes = -15,
                        cityName = selectedCity,
                        latDeg = latDeg,
                        lonDeg = lonDeg
                    )
                    AlarmCapability.saveRule(context, newRule)
                    rules = AlarmCapability.loadRules(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Add an alarm 15 minutes before moonrise", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }

        // List of Active Alarms
        items(rules) { rule ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.label,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${rule.event.displayName} (${rule.offsetMinutes} mins)",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "City: ${rule.cityName}",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = rule.copy(enabled = isChecked)
                            AlarmCapability.saveRule(context, updated)
                            rules = AlarmCapability.loadRules(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                    )
                }
            }
        }
    }
}

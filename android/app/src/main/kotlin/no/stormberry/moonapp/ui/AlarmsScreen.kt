package no.stormberry.moonapp.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import no.stormberry.moonapp.alarm.AlarmCapability
import no.stormberry.moonapp.alarm.OccurrenceEngine
import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.LunarEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The alarms half of the app: a list you can actually manage, and an editor behind it.
 *
 * ### What this replaces
 *
 * v1.0.0 had no alarms screen. The whole feature was one button on the times list that built a
 * hardcoded rule, `MoonAlarmRule(label = "Moonrise Alert", event = MOONRISE, offsetMinutes = -15)`,
 * with no way to choose any of the three. Four of the five [LunarEvent] values were unreachable,
 * every rule was identical and indistinguishable in the list, and `AlarmCapability.deleteRule`
 * was dead code, so an accidental double tap left two alarms firing forever with no way to
 * remove either short of clearing app data.
 *
 * ### The capability banner
 *
 * An armed alarm that cannot ring is the worst state this app has, because the switch says on.
 * Three separate platform grants can put it there, and the user is told which one is missing
 * and taken to the right Settings page rather than left to guess. This is the model SunApp's
 * PermissionSheet uses, reduced to what MoonApp actually needs.
 */
@Composable
fun AlarmsRoot(
    defaultPlace: ChosenPlace,
    onRulesChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(AlarmCapability.loadRules(context)) }
    var editing by remember { mutableStateOf<MoonAlarmRule?>(null) }
    var isNew by remember { mutableStateOf(false) }

    fun reload() {
        rules = AlarmCapability.loadRules(context)
        onRulesChanged()
    }

    val target = editing
    if (target != null) {
        RuleEditor(
            rule = target,
            isNew = isNew,
            onCancel = { editing = null },
            onSave = {
                AlarmCapability.saveRule(context, it)
                editing = null
                reload()
            },
            onDelete = {
                AlarmCapability.deleteRule(context, target.id)
                editing = null
                reload()
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Lunar alarms",
                style = MaterialTheme.typography.headlineSmall,
                color = Moon.TextPrimary,
            )
        }

        item { CapabilityBanner() }

        item {
            Button(
                onClick = {
                    isNew = true
                    editing = MoonAlarmRule(
                        id = UUID.randomUUID().toString(),
                        label = "Moonrise alarm",
                        event = LunarEvent.MOONRISE,
                        offsetMinutes = 0,
                        cityName = defaultPlace.label,
                        latDeg = defaultPlace.latDeg,
                        lonDeg = defaultPlace.lonDeg,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moon.Silver),
            ) {
                Text("New alarm", color = Moon.Background)
            }
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    "No alarms yet. An alarm is anchored to a lunar event at a place, so it " +
                        "moves with the moon instead of firing at a fixed clock time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Moon.TextSecondary,
                )
            }
        }

        items(rules, key = { it.id }) { rule ->
            AlarmRow(
                rule = rule,
                onToggle = { on ->
                    AlarmCapability.saveRule(context, rule.copy(enabled = on))
                    reload()
                },
                onEdit = { isNew = false; editing = rule },
            )
        }
    }
}

@Composable
private fun AlarmRow(rule: MoonAlarmRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm") }
    val next = remember(rule) {
        runCatching { OccurrenceEngine.findNextOccurrence(rule) }.getOrNull()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.label, style = MaterialTheme.typography.titleMedium, color = Moon.TextPrimary)
                Text(
                    "${rule.event.displayName} ${offsetLabel(rule.offsetMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Moon.TextSecondary,
                )
                Text(rule.cityName, style = MaterialTheme.typography.bodySmall, color = Moon.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!rule.enabled) "Off"
                    else next?.atZone(ZoneId.systemDefault())?.format(fmt)?.let { "Next: $it" }
                        ?: "No occurrence in the next 30 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.enabled) Moon.Blue else Moon.TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Edit or delete", color = Moon.Silver, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Moon.Background,
                    checkedTrackColor = Moon.Silver,
                    uncheckedThumbColor = Moon.TextMuted,
                    uncheckedTrackColor = Moon.Surface,
                ),
            )
        }
    }
}

/** "at the event", "15 minutes before", "1 h 30 min after". */
private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> "at the event"
    else -> {
        val abs = kotlin.math.abs(minutes)
        val h = abs / 60
        val m = abs % 60
        val span = when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
        if (minutes < 0) "$span before" else "$span after"
    }
}

/**
 * Says which platform grant is missing and opens the page that fixes it.
 *
 * Nothing here asks for a permission directly: POST_NOTIFICATIONS is requested by MainActivity
 * on first launch, and the other two are Settings toggles with no runtime request API. What
 * this does is stop the app lying, by refusing to show an alarm as armed and ringing when the
 * platform will silently drop it.
 */
@Composable
private fun CapabilityBanner() {
    val context = LocalContext.current

    val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    val fullScreenOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    if (notificationsOk && exactOk && fullScreenOk) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.CardHover),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "These alarms cannot ring yet",
                style = MaterialTheme.typography.titleMedium,
                color = Moon.Error,
            )
            Spacer(Modifier.height(6.dp))

            if (!notificationsOk) {
                BannerLine(
                    "Notifications are blocked, so a firing alarm has nothing to show and no " +
                        "way to take over the screen.",
                    "Open notification settings",
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            if (!exactOk) {
                BannerLine(
                    "Exact alarms are turned off. Android will not wake the device at the " +
                        "computed time.",
                    "Allow exact alarms",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            if (!fullScreenOk) {
                BannerLine(
                    "Full-screen alarms are turned off, so a locked phone will show a " +
                        "notification instead of the dismiss screen.",
                    "Allow full-screen alarms",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerLine(text: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Moon.TextSecondary)
        TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(action, color = Moon.Silver, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The editor: every field of a rule, and a preview of when it would actually fire.
 *
 * The preview exists for the same reason SunApp's does. A lunar event drifts about fifty
 * minutes a day, so "moonrise minus two hours" is a different clock time every night and a
 * rule that looks reasonable today can be firing at four in the afternoon a fortnight later.
 * Showing the next five occurrences at the point of creation makes that visible when the user
 * can still change their mind.
 */
@Composable
private fun RuleEditor(
    rule: MoonAlarmRule,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (MoonAlarmRule) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by remember(rule.id) { mutableStateOf(rule.label) }
    var event by remember(rule.id) { mutableStateOf(rule.event) }
    var offsetText by remember(rule.id) { mutableStateOf(rule.offsetMinutes.toString()) }
    var vibrate by remember(rule.id) { mutableStateOf(rule.vibrate) }

    val offset = offsetText.trim().toIntOrNull()
    val draft = rule.copy(
        label = label.ifBlank { event.displayName },
        event = event,
        offsetMinutes = offset ?: 0,
        vibrate = vibrate,
        enabled = true,
    )

    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm") }
    val preview = remember(draft.event, draft.offsetMinutes, draft.latDeg, draft.lonDeg) {
        val out = mutableListOf<Instant>()
        var cursor = Instant.now()
        repeat(5) {
            val next = runCatching { OccurrenceEngine.findNextOccurrence(draft, cursor) }.getOrNull()
                ?: return@repeat
            out.add(next)
            cursor = next.plusSeconds(60)
        }
        out.toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (isNew) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.headlineSmall,
                color = Moon.TextPrimary,
            )
        }

        item {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                colors = moonFieldColours(),
            )
        }

        item {
            Column {
                Text("Lunar event", style = MaterialTheme.typography.bodyMedium, color = Moon.TextSecondary)
                Spacer(Modifier.height(6.dp))
                for (e in LunarEvent.entries) {
                    FilterChip(
                        selected = event == e,
                        onClick = { event = e },
                        label = { Text(e.displayName) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Moon.Surface,
                            labelColor = Moon.TextSecondary,
                            selectedContainerColor = Moon.CardHover,
                            selectedLabelColor = Moon.Silver,
                        ),
                        border = null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Moon.TextMuted,
                )
            }
        }

        item {
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Offset in minutes (negative = before)") },
                isError = offset == null,
                supportingText = {
                    Text(
                        if (offset == null) "Enter a whole number, for example -15."
                        else offsetLabel(offset),
                        color = if (offset == null) Moon.Error else Moon.TextSecondary,
                    )
                },
                colors = moonFieldColours(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vibrate", style = MaterialTheme.typography.bodyMedium, color = Moon.TextPrimary)
                Switch(
                    checked = vibrate,
                    onCheckedChange = { vibrate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Moon.Background,
                        checkedTrackColor = Moon.Silver,
                    ),
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Moon.Card),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Next five times this would fire",
                        style = MaterialTheme.typography.titleMedium,
                        color = Moon.Silver,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        rule.cityName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Moon.TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (preview.isEmpty()) {
                        Text(
                            "This event does not occur at this location in the next 30 days. " +
                                "At high latitude the moon can stay up or down for days at a time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Moon.TextSecondary,
                        )
                    } else {
                        for (i in preview) {
                            Text(
                                i.atZone(ZoneId.systemDefault()).format(fmt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Moon.TextPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSave(draft) },
                    enabled = offset != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moon.Silver),
                ) { Text("Save", color = Moon.Background) }

                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moon.Surface),
                ) { Text("Cancel", color = Moon.TextSecondary) }
            }
        }

        if (!isNew) {
            item {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this alarm", color = Moon.Error)
                }
            }
        }
    }
}

package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppShadePreference
import com.example.data.PulseNotificationItem
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.MidnightSurface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ComposeCaptureDialog(
    onDismiss: () -> Unit,
    onSubmit: (SupportedApp, String, String) -> Unit
) {
    var selectedApp by rememberSaveable { mutableStateOf(SupportedApp.OUTLOOK) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Capture Message to Hub",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportedApp.filterableApps.forEach { app ->
                        FilterChip(
                            selected = selectedApp == app,
                            onClick = { selectedApp = app },
                            label = { Text(app.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Sender / Subject") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capture_title_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message body (URLs auto-extracted)") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capture_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedApp, title, body)
                    onDismiss()
                },
                enabled = title.isNotBlank() || body.isNotBlank(),
                modifier = Modifier.testTag("confirm_capture_button")
            ) {
                Text("Add to Feed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetReminderDialog(
    item: PulseNotificationItem,
    onDismiss: () -> Unit,
    onSchedule: (triggerAtMillis: Long, note: String?) -> Unit,
    onClearReminder: () -> Unit
) {
    val initialCal = remember(item.key, item.reminderAt) {
        Calendar.getInstance().apply {
            if (item.reminderAt != null && item.reminderAt > System.currentTimeMillis()) {
                timeInMillis = item.reminderAt
            } else {
                add(Calendar.MINUTE, 30)
            }
        }
    }
    val todayCal = remember { Calendar.getInstance() }
    val initialDayOffset = remember(initialCal) {
        if (initialCal.get(Calendar.DAY_OF_YEAR) != todayCal.get(Calendar.DAY_OF_YEAR) ||
            initialCal.get(Calendar.YEAR) != todayCal.get(Calendar.YEAR)
        ) 1 else 0
    }

    var dayOffset by remember { mutableIntStateOf(initialDayOffset) }
    var note by remember(item.key) { mutableStateOf(item.reminderNote.orEmpty()) }
    var selectedPresetMinutes by remember { mutableStateOf<Int?>(null) }

    val timeState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    val fmt = remember { SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Message Reminder",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${item.appLabel} • ${item.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )

                if (item.reminderAt != null) {
                    Text(
                        text = "Current: ${fmt.format(Date(item.reminderAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Quick presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        15 to "+15 min",
                        60 to "+1 hour",
                        180 to "+3 hours"
                    ).forEach { (mins, label) ->
                        FilterChip(
                            selected = selectedPresetMinutes == mins,
                            onClick = {
                                selectedPresetMinutes = mins
                                val target = System.currentTimeMillis() + mins * 60_000L
                                onSchedule(target, note.trim().takeIf { it.isNotEmpty() })
                                onDismiss()
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("reminder_preset_$mins")
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onSchedule(cal.timeInMillis, note.trim().takeIf { it.isNotEmpty() })
                            onDismiss()
                        },
                        label = { Text("Tomorrow 09:00") },
                        modifier = Modifier.testTag("reminder_preset_tomorrow")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dayOffset == 0,
                        onClick = { dayOffset = 0 },
                        label = { Text("Today") },
                        modifier = Modifier.testTag("reminder_day_today")
                    )
                    FilterChip(
                        selected = dayOffset == 1,
                        onClick = { dayOffset = 1 },
                        label = { Text("Tomorrow") },
                        modifier = Modifier.testTag("reminder_day_tomorrow")
                    )
                }

                TimeInput(
                    state = timeState,
                    modifier = Modifier.testTag("reminder_time_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reminder note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        if (dayOffset > 0) add(Calendar.DAY_OF_YEAR, dayOffset)
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // If chosen time today already passed, roll to tomorrow automatically
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    onSchedule(cal.timeInMillis, note.trim().takeIf { it.isNotEmpty() })
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_reminder_button")
            ) {
                Text("Save Reminder")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.reminderAt != null) {
                    TextButton(
                        onClick = {
                            onClearReminder()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("clear_reminder_button")
                    ) {
                        Text("Clear", color = CrimsonError)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ShadePreferencesDialog(
    defaultShadeMode: ShadeMode,
    appPreferences: List<AppShadePreference>,
    hasNotificationPermission: Boolean,
    onSetDefaultShadeMode: (ShadeMode) -> Unit,
    onSetAppOptedIn: (String, Boolean) -> Unit,
    onSetAppShadeMode: (String, ShadeMode) -> Unit,
    onOpenSystemNotificationSettings: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "App Interception & Shade Rules",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All apps start opted in so Nepho captures notifications and suppresses duplicates. Toggle an app off to opt out, pick Silence for feed-only capture, or Block to stop its notifications entirely. Silencing or blocking also opens that app's system notification settings so you can apply the same rule OS-wide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightSurface,
                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Allow notifications to see Nepho Alerts in shade",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.testTag("dialog_allow_shade_alerts_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                ShadeModeRow(
                    title = "Default for Other Apps",
                    subtitle = "Newly observed apps start opted in",
                    isOptedIn = true,
                    showOptInSwitch = false,
                    onToggleOptedIn = {},
                    selectedMode = defaultShadeMode,
                    tagPrefix = "default",
                    allowBlock = false,
                    onSelectMode = onSetDefaultShadeMode,
                    onOpenSystemSettings = null
                )

                HorizontalDivider(color = BorderHairline)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = appPreferences, key = { it.packageName }) { pref ->
                        ShadeModeRow(
                            title = pref.appLabel,
                            subtitle = if (pref.isCoreApp) "Core app" else pref.packageName,
                            isOptedIn = pref.isOptedIn,
                            showOptInSwitch = true,
                            onToggleOptedIn = { optedIn -> onSetAppOptedIn(pref.packageName, optedIn) },
                            selectedMode = pref.shadeMode,
                            tagPrefix = pref.packageName,
                            allowBlock = true,
                            onSelectMode = { mode -> onSetAppShadeMode(pref.packageName, mode) },
                            onOpenSystemSettings = { onOpenSystemNotificationSettings(pref.packageName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_shade_prefs_button")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ShadeModeRow(
    title: String,
    subtitle: String,
    isOptedIn: Boolean,
    showOptInSwitch: Boolean,
    onToggleOptedIn: (Boolean) -> Unit,
    selectedMode: ShadeMode,
    tagPrefix: String,
    allowBlock: Boolean,
    onSelectMode: (ShadeMode) -> Unit,
    onOpenSystemSettings: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onOpenSystemSettings != null) {
                IconButton(
                    onClick = onOpenSystemSettings,
                    modifier = Modifier
                        .semantics { contentDescription = "Open system notification settings for $title" }
                        .testTag("system_settings_$tagPrefix")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showOptInSwitch) {
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isOptedIn,
                    onCheckedChange = onToggleOptedIn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MidnightSurface,
                        checkedTrackColor = CyanPulse
                    ),
                    modifier = Modifier
                        .semantics { contentDescription = "Intercept $title notifications" }
                        .testTag("opt_in_switch_$tagPrefix")
                )
            }
        }

        if (isOptedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == ShadeMode.SILENT,
                    onClick = { onSelectMode(ShadeMode.SILENT) },
                    label = { Text("Silence (Feed Only)") },
                    modifier = Modifier.testTag("shade_silent_$tagPrefix"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPulseContainer,
                        selectedLabelColor = CyanPulse
                    )
                )
                FilterChip(
                    selected = selectedMode == ShadeMode.NEPHO_ALERT,
                    onClick = { onSelectMode(ShadeMode.NEPHO_ALERT) },
                    label = { Text("Nepho Alert") },
                    modifier = Modifier.testTag("shade_alert_$tagPrefix"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPulseContainer,
                        selectedLabelColor = CyanPulse
                    )
                )
                if (allowBlock) {
                    FilterChip(
                        selected = selectedMode == ShadeMode.BLOCKED,
                        onClick = { onSelectMode(ShadeMode.BLOCKED) },
                        label = { Text("Block") },
                        modifier = Modifier.testTag("shade_blocked_$tagPrefix"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonError.copy(alpha = 0.18f),
                            selectedLabelColor = CrimsonError
                        )
                    )
                }
            }
            if (selectedMode == ShadeMode.BLOCKED) {
                Text(
                    text = "Blocked — notifications are cancelled and never reach the feed or shade",
                    style = MaterialTheme.typography.bodySmall,
                    color = CrimsonError,
                    modifier = Modifier.testTag("blocked_label_$tagPrefix")
                )
            }
        } else {
            Text(
                text = "Opted out — original app alerts pass through untouched",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("opted_out_label_$tagPrefix")
            )
        }
    }
}

package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppShadePreference
import com.example.data.PulseNotificationItem
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.data.local.CalmVisualPrefs
import com.example.data.local.HomeSectionId
import com.example.data.local.HomeSectionPrefs
import com.example.ui.theme.LocalNephoPalette
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
    val palette = LocalNephoPalette.current
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
                        Text("Clear", color = palette.crimson)
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
    val palette = LocalNephoPalette.current

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
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f))
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

                HorizontalDivider(color = palette.border)

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
    val palette = LocalNephoPalette.current

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
                        checkedThumbColor = palette.surface,
                        checkedTrackColor = palette.accent
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
                        selectedContainerColor = palette.accentContainer,
                        selectedLabelColor = palette.accent
                    )
                )
                FilterChip(
                    selected = selectedMode == ShadeMode.NEPHO_ALERT,
                    onClick = { onSelectMode(ShadeMode.NEPHO_ALERT) },
                    label = { Text("Nepho Alert") },
                    modifier = Modifier.testTag("shade_alert_$tagPrefix"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accentContainer,
                        selectedLabelColor = palette.accent
                    )
                )
                if (allowBlock) {
                    FilterChip(
                        selected = selectedMode == ShadeMode.BLOCKED,
                        onClick = { onSelectMode(ShadeMode.BLOCKED) },
                        label = { Text("Block") },
                        modifier = Modifier.testTag("shade_blocked_$tagPrefix"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.crimsonContainer,
                            selectedLabelColor = palette.crimson
                        )
                    )
                }
            }
            if (selectedMode == ShadeMode.BLOCKED) {
                Text(
                    text = "Blocked — notifications are cancelled and never reach the feed or shade",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.crimson,
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

@Composable
fun HomeSectionsDialog(
    sections: HomeSectionPrefs,
    onSetSections: (HomeSectionPrefs) -> Unit,
    onDismiss: () -> Unit,
    calmPrefs: CalmVisualPrefs = CalmVisualPrefs(),
    onSetCalmPrefs: (CalmVisualPrefs) -> Unit = {},
    onResetLayout: (() -> Unit)? = null
) {
    val palette = LocalNephoPalette.current
    val orderedSections = sections.normalizedOrder

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.action_home_layout_and_calm),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_layout_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                orderedSections.forEachIndexed { index, sectionId ->
                    val label = when (sectionId) {
                        HomeSectionId.CLOCK -> stringResource(R.string.home_section_clock)
                        HomeSectionId.MUSIC -> stringResource(R.string.home_section_music)
                        HomeSectionId.CALENDAR -> stringResource(R.string.home_section_calendar)
                        HomeSectionId.INBOX -> stringResource(R.string.home_section_inbox)
                        HomeSectionId.APPS -> stringResource(R.string.home_section_app_drawer)
                    }
                    val toggleTag = when (sectionId) {
                        HomeSectionId.CLOCK -> "home_section_toggle_clock"
                        HomeSectionId.MUSIC -> "home_section_toggle_music"
                        HomeSectionId.CALENDAR -> "home_section_toggle_calendar"
                        HomeSectionId.INBOX -> "home_section_toggle_inbox"
                        HomeSectionId.APPS -> "home_section_toggle_app_drawer"
                    }
                    val idKey = sectionId.name.lowercase()
                    val isVisible = sections.isVisible(sectionId)
                    val isMinimized = sections.isMinimized(sectionId)

                    HomeSectionOrderRow(
                        label = label,
                        visible = isVisible,
                        minimized = isMinimized,
                        canMoveUp = index > 0,
                        canMoveDown = index < orderedSections.lastIndex,
                        toggleTag = toggleTag,
                        idKey = idKey,
                        onToggleVisible = { onSetSections(sections.withVisibility(sectionId, it)) },
                        onToggleMinimized = { onSetSections(sections.toggleMinimized(sectionId)) },
                        onMoveUp = { onSetSections(sections.moveSection(sectionId, -1)) },
                        onMoveDown = { onSetSections(sections.moveSection(sectionId, 1)) }
                    )
                }

                HomeSectionToggleRow(
                    label = stringResource(R.string.home_section_dock),
                    checked = sections.showShortcutDock,
                    testTag = "home_section_toggle_dock",
                    onCheckedChange = { onSetSections(sections.copy(showShortcutDock = it)) }
                )

                HorizontalDivider(
                    color = palette.border,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = stringResource(R.string.calm_section_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.accent
                )

                CalmToggleRow(
                    title = stringResource(R.string.calm_palette_label),
                    description = stringResource(R.string.calm_palette_desc),
                    checked = calmPrefs.calmPaletteEnabled,
                    testTag = "calm_toggle_palette",
                    onCheckedChange = {
                        onSetCalmPrefs(calmPrefs.copy(calmPaletteEnabled = it))
                    }
                )
                CalmToggleRow(
                    title = stringResource(R.string.calm_monochrome_label),
                    description = stringResource(R.string.calm_monochrome_desc),
                    checked = calmPrefs.monochromeIcons,
                    testTag = "calm_toggle_monochrome",
                    onCheckedChange = {
                        onSetCalmPrefs(calmPrefs.copy(monochromeIcons = it))
                    }
                )
                CalmToggleRow(
                    title = stringResource(R.string.calm_hide_counts_label),
                    description = stringResource(R.string.calm_hide_counts_desc),
                    checked = calmPrefs.hideUnreadCounts,
                    testTag = "calm_toggle_hide_counts",
                    onCheckedChange = {
                        onSetCalmPrefs(calmPrefs.copy(hideUnreadCounts = it))
                    }
                )
            }
        },
        dismissButton = if (onResetLayout != null) {
            {
                TextButton(
                    onClick = onResetLayout,
                    modifier = Modifier.testTag("home_layout_reset_button")
                ) {
                    Text(stringResource(R.string.home_layout_reset))
                }
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("home_sections_done_button")
            ) {
                Text(stringResource(R.string.dock_done_editing))
            }
        }
    )
}

@Composable
private fun CalmToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalNephoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.surface,
                checkedTrackColor = palette.accent
            ),
            modifier = Modifier
                .semantics { contentDescription = title }
                .testTag(testTag)
        )
    }
}

@Composable
private fun HomeSectionOrderRow(
    label: String,
    visible: Boolean,
    minimized: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    toggleTag: String,
    idKey: String,
    onToggleVisible: (Boolean) -> Unit,
    onToggleMinimized: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val palette = LocalNephoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleVisible(!visible) }
                .padding(horizontal = 4.dp)
                .testTag(toggleTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = visible,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = palette.accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (visible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleMinimized,
            enabled = visible,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("home_section_minimize_$idKey")
        ) {
            Icon(
                imageVector = if (minimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (minimized) {
                    stringResource(R.string.home_section_expand, label)
                } else {
                    stringResource(R.string.home_section_minimize, label)
                },
                tint = when {
                    !visible -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    minimized -> palette.accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("home_section_move_up_$idKey")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.home_section_move_up, label),
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("home_section_move_down_$idKey")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.home_section_move_down, label),
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HomeSectionToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalNephoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = palette.accent)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

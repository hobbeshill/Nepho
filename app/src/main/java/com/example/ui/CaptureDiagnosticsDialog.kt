package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CaptureOutcome
import com.example.data.local.CaptureDiagnosticEntity
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSignalContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.EmeraldOkContainer
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DiagnosticsFilter {
    ALL,
    CAPTURED,
    SKIPPED_AND_SYNCED
}

@Composable
fun CaptureDiagnosticsDialog(
    health: ListenerHealthState,
    diagnostics: List<CaptureDiagnosticEntity>,
    onRequestRebind: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onClearLog: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf(DiagnosticsFilter.ALL) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val shortTimeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val filteredEntries = remember(diagnostics, selectedFilter) {
        when (selectedFilter) {
            DiagnosticsFilter.ALL -> diagnostics
            DiagnosticsFilter.CAPTURED -> diagnostics.filter {
                it.outcome == CaptureOutcome.CAPTURED.name ||
                    it.outcome == CaptureOutcome.CAPTURED_CATCHUP.name
            }
            DiagnosticsFilter.SKIPPED_AND_SYNCED -> diagnostics.filter {
                it.outcome != CaptureOutcome.CAPTURED.name &&
                    it.outcome != CaptureOutcome.CAPTURED_CATCHUP.name
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    tint = CyanPulse
                )
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ListenerHealthCard(
                    health = health,
                    shortTimeFormatter = shortTimeFormatter,
                    onRequestRebind = onRequestRebind,
                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenBatterySettings = onOpenBatterySettings
                )

                HorizontalDivider(color = BorderHairline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_recent_activity),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (diagnostics.isNotEmpty()) {
                        TextButton(
                            onClick = onClearLog,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diagnostics_clear_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.diagnostics_action_clear),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticsFilterChip(
                        selected = selectedFilter == DiagnosticsFilter.ALL,
                        label = stringResource(R.string.diagnostics_filter_all),
                        testTag = "diagnostics_filter_all",
                        onClick = { selectedFilter = DiagnosticsFilter.ALL }
                    )
                    DiagnosticsFilterChip(
                        selected = selectedFilter == DiagnosticsFilter.CAPTURED,
                        label = stringResource(R.string.diagnostics_filter_captured),
                        testTag = "diagnostics_filter_captured",
                        onClick = { selectedFilter = DiagnosticsFilter.CAPTURED }
                    )
                    DiagnosticsFilterChip(
                        selected = selectedFilter == DiagnosticsFilter.SKIPPED_AND_SYNCED,
                        label = stringResource(R.string.diagnostics_filter_skipped),
                        testTag = "diagnostics_filter_skipped",
                        onClick = { selectedFilter = DiagnosticsFilter.SKIPPED_AND_SYNCED }
                    )
                }

                if (filteredEntries.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElevatedSurface,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostics_empty_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.diagnostics_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                            .testTag("diagnostics_log_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = filteredEntries, key = { it.id }) { entry ->
                            DiagnosticEntryRow(
                                entry = entry,
                                timeText = timeFormatter.format(Date(entry.timestamp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("diagnostics_close_button")
            ) {
                Text(stringResource(R.string.diagnostics_close), color = CyanPulse)
            }
        }
    )
}

@Composable
private fun ListenerHealthCard(
    health: ListenerHealthState,
    shortTimeFormatter: SimpleDateFormat,
    onRequestRebind: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ElevatedSurface,
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_health_header),
                style = MaterialTheme.typography.labelLarge,
                color = CyanPulse
            )

            val listenerStatusColor = when {
                !health.isAccessGranted -> CrimsonError
                health.isListenerConnected -> EmeraldOk
                else -> AmberSignal
            }
            val listenerStatusText = when {
                !health.isAccessGranted -> stringResource(R.string.diagnostics_listener_no_access)
                health.isListenerConnected && health.lastConnectedAt != null -> stringResource(
                    R.string.diagnostics_listener_connected_since,
                    shortTimeFormatter.format(Date(health.lastConnectedAt))
                )
                health.isListenerConnected -> stringResource(R.string.diagnostics_listener_connected)
                else -> stringResource(R.string.diagnostics_listener_disconnected)
            }

            HealthStatusRow(
                label = stringResource(R.string.diagnostics_listener_label),
                statusText = listenerStatusText,
                statusColor = listenerStatusColor,
                actionContent = {
                    if (health.isAccessGranted) {
                        TextButton(
                            onClick = onRequestRebind,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diagnostics_reconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CyanPulse
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.diagnostics_action_reconnect),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onOpenNotificationAccessSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diagnostics_access_button")
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostics_action_access),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }
                    }
                }
            )

            HealthStatusRow(
                label = stringResource(R.string.diagnostics_shade_perm_label),
                statusText = stringResource(
                    if (health.hasPostNotificationPermission) {
                        R.string.diagnostics_perm_granted
                    } else {
                        R.string.diagnostics_perm_missing
                    }
                ),
                statusColor = if (health.hasPostNotificationPermission) EmeraldOk else AmberSignal,
                actionContent = {
                    if (!health.hasPostNotificationPermission) {
                        TextButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diagnostics_permission_button")
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostics_action_allow),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }
                    }
                }
            )

            HealthStatusRow(
                label = stringResource(R.string.diagnostics_battery_label),
                statusText = stringResource(
                    if (health.isIgnoringBatteryOptimizations) {
                        R.string.diagnostics_battery_unrestricted
                    } else {
                        R.string.diagnostics_battery_optimized
                    }
                ),
                statusColor = if (health.isIgnoringBatteryOptimizations) EmeraldOk else AmberSignal,
                actionContent = {
                    TextButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("diagnostics_battery_button")
                    ) {
                        Text(
                            text = stringResource(R.string.diagnostics_action_battery),
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanPulse
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun HealthStatusRow(
    label: String,
    statusText: String,
    statusColor: Color,
    actionContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actionContent()
    }
}

@Composable
private fun DiagnosticsFilterChip(
    selected: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanPulseContainer,
            selectedLabelColor = CyanPulse
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    )
}

@Composable
private fun DiagnosticEntryRow(
    entry: CaptureDiagnosticEntity,
    timeText: String
) {
    val (badgeLabel, badgeTextColor, badgeBgColor) = outcomeBadgeStyle(entry.outcome)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ElevatedSurface,
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Text(
                text = "${entry.appLabel} • ${entry.titlePreview}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun outcomeBadgeStyle(outcomeName: String): Triple<String, Color, Color> = when (outcomeName) {
    CaptureOutcome.CAPTURED.name -> Triple("CAPTURED", EmeraldOk, EmeraldOkContainer)
    CaptureOutcome.CAPTURED_CATCHUP.name -> Triple("CATCH-UP", CyanPulse, CyanPulseContainer)
    CaptureOutcome.LISTENER_CONNECTED.name -> Triple("CONNECTED", CyanPulse, CyanPulseContainer)
    CaptureOutcome.LISTENER_DISCONNECTED.name -> Triple("DISCONNECTED", CrimsonError, CrimsonErrorContainer)
    CaptureOutcome.SKIPPED_BLOCKED.name -> Triple("BLOCKED", CrimsonError, CrimsonErrorContainer)
    CaptureOutcome.SKIPPED_OPTED_OUT.name -> Triple("OPTED OUT", AmberSignal, AmberSignalContainer)
    CaptureOutcome.SKIPPED_GROUP_SUMMARY.name -> Triple("SUMMARY SKIPPED", AmberSignal, AmberSignalContainer)
    CaptureOutcome.SKIPPED_PRE_SESSION.name -> Triple("PRE-SESSION", AmberSignal, AmberSignalContainer)
    CaptureOutcome.FALLBACK_KEPT_ORIGINAL.name -> Triple("KEPT ORIGINAL", AmberSignal, AmberSignalContainer)
    CaptureOutcome.SELF_CANCEL_IGNORED.name -> Triple("RACE GUARD", CyanPulse, CyanPulseContainer)
    CaptureOutcome.SYNCED_REMOVAL.name -> Triple("SYNCED CLEAR", AmberSignal, AmberSignalContainer)
    else -> Triple(outcomeName, TextSecondary, MidnightSurface)
}

package com.example.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.launcher.CalendarDayGroup
import com.example.launcher.CalendarEventItem
import com.example.launcher.CalendarScheduleProvider
import com.example.launcher.CalendarScheduleState
import com.example.ui.theme.LocalCalmVisualPrefs
import com.example.ui.theme.LocalNephoPalette

@Composable
fun HomeCalendarCard(
    scheduleState: CalendarScheduleState,
    onRequestCalendarPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenEvent: (CalendarEventItem) -> Unit,
    onOpenCalendarApp: () -> Unit,
    onMinimize: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNephoPalette.current
    val calmPrefs = LocalCalmVisualPrefs.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val allDayLabel = stringResource(R.string.calendar_all_day)

    val calendarCardShape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(calendarCardShape)
            .calmingLitBorder(
                accentColor = palette.accent,
                cornerRadius = 20.dp,
                strokeWidth = 1.15.dp,
                intensity = CalmBorderIntensity.AMBIENT
            )
            .testTag("home_calendar_card"),
        shape = calendarCardShape,
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val iconTileShape = RoundedCornerShape(12.dp)
                Surface(
                    shape = iconTileShape,
                    color = palette.accentContainer,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(iconTileShape)
                        .calmingLitBorder(
                            accentColor = palette.accent,
                            cornerRadius = 12.dp,
                            strokeWidth = 1.dp,
                            intensity = CalmBorderIntensity.SUBTLE
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_card_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_calendar_title")
                        )
                        if (scheduleState.hasPermission && scheduleState.totalEventCount > 0 && !calmPrefs.hideUnreadCounts) {
                            Surface(
                                shape = CircleShape,
                                color = palette.accentContainer,
                                modifier = Modifier.testTag("calendar_total_count_badge")
                            ) {
                                Text(
                                    text = scheduleState.totalEventCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = palette.accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.calendar_window_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scheduleState.hasPermission) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !scheduleState.isLoading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_refresh_button")
                        ) {
                            if (scheduleState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.calendar_refresh),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenCalendarApp,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_open_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.calendar_open_app),
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (onMinimize != null) {
                        SectionCollapseIconButton(
                            minimized = false,
                            sectionName = stringResource(R.string.home_section_calendar),
                            onToggle = onMinimize,
                            testTag = "minimize_section_calendar"
                        )
                    }
                }
            }

            if (!scheduleState.hasPermission) {
                val promptShape = RoundedCornerShape(14.dp)
                Surface(
                    shape = promptShape,
                    color = palette.elevatedSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(promptShape)
                        .calmingLitBorder(
                            accentColor = palette.accent,
                            cornerRadius = 14.dp,
                            strokeWidth = 1.dp,
                            intensity = CalmBorderIntensity.SUBTLE
                        )
                        .testTag("calendar_permission_prompt")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.calendar_permission_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.calendar_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestCalendarPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.background
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_grant_permission_button")
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_grant_access),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            } else {
                val daysToDisplay = remember(scheduleState.days) {
                    scheduleState.days.ifEmpty {
                        CalendarScheduleProvider.groupEventsIntoThreeDays(emptyList())
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    daysToDisplay.forEachIndexed { index, dayGroup ->
                        if (index > 0) {
                            HorizontalDivider(color = palette.border.copy(alpha = 0.65f))
                        }
                        CalendarDaySection(
                            dayGroup = dayGroup,
                            is24Hour = is24Hour,
                            allDayLabel = allDayLabel,
                            onOpenEvent = onOpenEvent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDaySection(
    dayGroup: CalendarDayGroup,
    is24Hour: Boolean,
    allDayLabel: String,
    onOpenEvent: (CalendarEventItem) -> Unit
) {
    val palette = LocalNephoPalette.current
    val calmPrefs = LocalCalmVisualPrefs.current
    val dayAccent = when (dayGroup.dayOffset) {
        0 -> palette.accent
        1 -> palette.secondaryAccent
        else -> palette.emerald
    }
    val dayBadgeContainer = when (dayGroup.dayOffset) {
        0 -> palette.accentContainer
        1 -> palette.secondaryContainer
        else -> palette.highSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_day_section_${dayGroup.dayOffset}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dayBadgeShape = RoundedCornerShape(8.dp)
                Surface(
                    shape = dayBadgeShape,
                    color = dayBadgeContainer,
                    modifier = Modifier
                        .clip(dayBadgeShape)
                        .calmingLitBorder(
                            accentColor = dayAccent,
                            cornerRadius = 8.dp,
                            strokeWidth = 1.dp,
                            intensity = CalmBorderIntensity.SUBTLE
                        )
                ) {
                    Text(
                        text = dayGroup.dayTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = dayAccent,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("calendar_day_title_${dayGroup.dayOffset}")
                    )
                }
                Text(
                    text = dayGroup.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (dayGroup.events.isNotEmpty() && !calmPrefs.hideUnreadCounts) {
                Text(
                    text = "${dayGroup.events.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (dayGroup.events.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_nothing_scheduled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier
                    .padding(start = 4.dp, top = 1.dp, bottom = 2.dp)
                    .testTag("calendar_day_empty_${dayGroup.dayOffset}")
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dayGroup.events.forEach { event ->
                    CalendarEventRow(
                        event = event,
                        dayOffset = dayGroup.dayOffset,
                        fallbackAccent = dayAccent,
                        is24Hour = is24Hour,
                        allDayLabel = allDayLabel,
                        onClick = { onOpenEvent(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(
    event: CalendarEventItem,
    dayOffset: Int,
    fallbackAccent: Color,
    is24Hour: Boolean,
    allDayLabel: String,
    onClick: () -> Unit
) {
    val palette = LocalNephoPalette.current
    val accentColor = remember(event.calendarColor, fallbackAccent, palette.isCalmMode) {
        if (palette.isCalmMode) {
            fallbackAccent
        } else {
            val raw = event.calendarColor
            if (raw != null && raw != 0) {
                Color(raw).copy(alpha = 1f)
            } else {
                fallbackAccent
            }
        }
    }
    val timeText = remember(event, is24Hour, allDayLabel) {
        CalendarScheduleProvider.formatEventTimeRange(
            event = event,
            is24Hour = is24Hour,
            allDayLabel = allDayLabel
        )
    }

    val eventRowShape = RoundedCornerShape(12.dp)
    Surface(
        shape = eventRowShape,
        color = palette.elevatedSurface,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(eventRowShape)
            .calmingLitBorder(
                accentColor = accentColor,
                cornerRadius = 12.dp,
                strokeWidth = 1.dp,
                intensity = CalmBorderIntensity.SUBTLE
            )
            .clickable(onClick = onClick)
            .testTag("calendar_event_${event.eventId}_$dayOffset")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calendar_event_title_${event.eventId}_$dayOffset")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.allDay) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (event.location.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

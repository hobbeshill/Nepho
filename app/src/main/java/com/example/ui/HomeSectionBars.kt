package com.example.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.launcher.CalendarScheduleProvider
import com.example.launcher.CalendarScheduleState
import com.example.launcher.MediaPlaybackState
import com.example.launcher.WeatherUiState
import com.example.ui.theme.LocalCalmVisualPrefs
import com.example.ui.theme.LocalNephoPalette
import com.example.ui.theme.NephoPalette
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionCollapseIconButton(
    minimized: Boolean,
    sectionName: String,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = if (minimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (minimized) {
                stringResource(R.string.home_section_expand, sectionName)
            } else {
                stringResource(R.string.home_section_minimize, sectionName)
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Immutable
data class CalmBlueLightingState(
    val active: Boolean,
    val breath: Float,
    val sweepPhase: Float,
    val headingColor: Color,
    val borderColor: Color,
    val containerColor: Color,
    val textShadow: Shadow?
)

@Composable
fun rememberCalmBlueLightingState(active: Boolean): CalmBlueLightingState {
    val palette = LocalNephoPalette.current
    val sharedPhase = LocalCalmLightingPhase.current
    if (!active) {
        return CalmBlueLightingState(
            active = false,
            breath = sharedPhase.breath,
            sweepPhase = -0.35f + sharedPhase.sweep * 1.70f,
            headingColor = MaterialTheme.colorScheme.onSurface,
            borderColor = palette.border,
            containerColor = palette.surface,
            textShadow = null
        )
    }

    val breath = sharedPhase.breath
    val sweepPhase = -0.35f + sharedPhase.sweep * 1.70f

    val headingColor = lerp(palette.calmBlue, palette.calmBlueBright, breath)
    val borderColor = lerp(
        palette.calmBlue.copy(alpha = 0.28f),
        palette.calmBlueBright.copy(alpha = 0.62f),
        breath
    )
    val containerColor = lerp(
        palette.surface,
        palette.calmBlueSurface,
        0.45f + breath * 0.40f
    )
    val textShadow = Shadow(
        color = palette.calmBlue.copy(alpha = 0.24f + breath * 0.30f),
        offset = Offset.Zero,
        blurRadius = 10f + breath * 8f
    )

    return CalmBlueLightingState(
        active = true,
        breath = breath,
        sweepPhase = sweepPhase,
        headingColor = headingColor,
        borderColor = borderColor,
        containerColor = containerColor,
        textShadow = textShadow
    )
}

fun Modifier.calmBlueLightingBackdrop(
    lighting: CalmBlueLightingState,
    palette: NephoPalette
): Modifier {
    if (!lighting.active) return this
    return this.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        // Soft ambient breathing wash
        drawRect(
            color = palette.calmBlueDeep.copy(alpha = 0.12f + lighting.breath * 0.16f)
        )
        // Slow drifting calming blue light wave
        val centerX = lighting.sweepPhase * size.width
        val halfBand = size.width * 0.42f
        val startX = centerX - halfBand
        val endX = centerX + halfBand
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.35f to palette.calmBlue.copy(alpha = 0.10f + lighting.breath * 0.08f),
                    0.50f to palette.calmBlueBright.copy(alpha = 0.16f + lighting.breath * 0.10f),
                    0.65f to palette.calmBlue.copy(alpha = 0.10f + lighting.breath * 0.08f),
                    1.0f to Color.Transparent
                ),
                startX = startX,
                endX = endX
            )
        )
    }
}

@Composable
private fun MinimizedSectionContainer(
    icon: ImageVector,
    testTag: String,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
    trailingAction: @Composable (() -> Unit)? = null,
    iconTint: Color? = null,
    containerColor: Color? = null,
    borderAccentColor: Color? = null,
    borderIntensity: CalmBorderIntensity = CalmBorderIntensity.AMBIENT,
    backdropModifier: Modifier = Modifier
) {
    val palette = LocalNephoPalette.current
    val resolvedAccent = borderAccentColor ?: palette.accent
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = containerColor ?: palette.surface,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(shape)
            .calmingLitBorder(
                accentColor = resolvedAccent,
                cornerRadius = 16.dp,
                strokeWidth = if (borderIntensity == CalmBorderIntensity.ACTIVE) 1.4.dp else 1.1.dp,
                intensity = borderIntensity
            )
            .clickable(onClick = onExpand)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(backdropModifier)
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            if (trailingAction != null) {
                trailingAction()
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
fun MinimizedClockBar(
    weatherState: WeatherUiState,
    onExpand: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalNephoPalette.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val msIntoSecond = nowMillis % 1_000L
            delay((1_000L - msIntoSecond).coerceAtLeast(250L))
        }
    }

    val timeFmt = remember(is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val nowDate = Date(nowMillis)

    MinimizedSectionContainer(
        icon = Icons.Filled.AccessTime,
        testTag = "minimized_section_clock",
        onExpand = onExpand,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timeFmt.format(nowDate),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Text(
                    text = dateFmt.format(nowDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (weatherState.hasWeatherData) {
                    Text(
                        text = "• ${weatherState.displayTemp}${weatherState.unitSuffix}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

@Composable
fun MinimizedMusicBar(
    mediaState: MediaPlaybackState,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    val palette = LocalNephoPalette.current
    MinimizedSectionContainer(
        icon = Icons.Filled.MusicNote,
        testTag = "minimized_section_music",
        onExpand = onExpand,
        content = {
            val label = if (mediaState.isActive) {
                val track = mediaState.title.ifBlank { stringResource(R.string.music_idle_title) }
                if (mediaState.artist.isNotBlank()) "$track • ${mediaState.artist}" else track
            } else {
                stringResource(R.string.minimized_music_idle)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mediaState.isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingAction = if (mediaState.isActive) {
            {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("minimized_music_play_pause")
                ) {
                    Icon(
                        imageVector = if (mediaState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (mediaState.isPlaying) {
                            stringResource(R.string.music_action_pause)
                        } else {
                            stringResource(R.string.music_action_play)
                        },
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            null
        }
    )
}

@Composable
fun MinimizedCalendarBar(
    scheduleState: CalendarScheduleState,
    onExpand: () -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val allDayLabel = stringResource(R.string.calendar_all_day)
    val nextEvent = remember(scheduleState.days) {
        scheduleState.days.firstOrNull { it.events.isNotEmpty() }?.let { day ->
            day.dayTitle to day.events.first()
        }
    }

    MinimizedSectionContainer(
        icon = Icons.Filled.CalendarMonth,
        testTag = "minimized_section_calendar",
        onExpand = onExpand,
        content = {
            val summary = when {
                !scheduleState.hasPermission -> stringResource(R.string.minimized_calendar_connect)
                nextEvent != null -> {
                    val (dayTitle, event) = nextEvent
                    val timeText = CalendarScheduleProvider.formatEventTimeRange(
                        event = event,
                        is24Hour = is24Hour,
                        allDayLabel = allDayLabel
                    )
                    "$dayTitle • ${event.title} ($timeText)"
                }
                else -> stringResource(R.string.minimized_calendar_clear)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (nextEvent != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
fun MinimizedInboxBar(
    unreadCount: Int,
    linksCount: Int,
    onExpand: () -> Unit,
    totalInboxCount: Int = unreadCount
) {
    val calmPrefs = LocalCalmVisualPrefs.current
    val palette = LocalNephoPalette.current
    val hasInboxItems = totalInboxCount > 0 || unreadCount > 0
    val lighting = rememberCalmBlueLightingState(active = hasInboxItems)

    MinimizedSectionContainer(
        icon = Icons.Filled.DynamicFeed,
        testTag = "minimized_section_inbox",
        onExpand = onExpand,
        iconTint = if (hasInboxItems) lighting.headingColor else palette.accent,
        containerColor = lighting.containerColor,
        borderAccentColor = if (hasInboxItems) palette.calmBlue else palette.accent,
        borderIntensity = if (hasInboxItems) CalmBorderIntensity.ACTIVE else CalmBorderIntensity.AMBIENT,
        backdropModifier = Modifier.calmBlueLightingBackdrop(lighting, palette),
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.minimized_inbox_label),
                    style = if (hasInboxItems) {
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = lighting.textShadow
                        )
                    } else {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = if (hasInboxItems) {
                        lighting.headingColor
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.testTag("minimized_inbox_heading")
                )
                if (calmPrefs.hideUnreadCounts) {
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(lighting.headingColor)
                                .testTag("minimized_inbox_quiet_dot")
                        )
                        Text(
                            text = stringResource(R.string.calm_inbox_new_activity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("minimized_inbox_status")
                        )
                    } else if (totalInboxCount > 0) {
                        Text(
                            text = stringResource(R.string.calm_inbox_reviewed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("minimized_inbox_status")
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.calm_inbox_caught_up),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("minimized_inbox_status")
                        )
                    }
                } else {
                    val parts = buildList {
                        if (unreadCount > 0) add("$unreadCount unread")
                        else if (totalInboxCount > 0) add("$totalInboxCount alert${if (totalInboxCount == 1) "" else "s"}")
                        if (linksCount > 0) add("$linksCount links")
                    }
                    Text(
                        text = if (parts.isNotEmpty()) {
                            parts.joinToString(" • ")
                        } else {
                            stringResource(R.string.calm_inbox_caught_up)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}

@Composable
fun MinimizedAppDrawerBar(
    totalInstalledCount: Int,
    onExpand: () -> Unit,
    onQuickSearch: () -> Unit
) {
    val calmPrefs = LocalCalmVisualPrefs.current
    val palette = LocalNephoPalette.current

    MinimizedSectionContainer(
        icon = Icons.Filled.Apps,
        testTag = "minimized_section_apps",
        onExpand = onExpand,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.minimized_apps_label),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!calmPrefs.hideUnreadCounts && totalInstalledCount > 0) {
                    Text(
                        text = stringResource(R.string.drawer_app_count, totalInstalledCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        trailingAction = {
            IconButton(
                onClick = onQuickSearch,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("minimized_apps_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.minimized_apps_search_desc),
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

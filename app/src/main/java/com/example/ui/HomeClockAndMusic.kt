package com.example.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.launcher.ClockAlarmHelper
import com.example.launcher.LauncherAppInfo
import com.example.launcher.MediaPlaybackState
import com.example.launcher.WeatherUiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSignalContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.EmeraldOkContainer
import com.example.ui.theme.HighSurface
import com.example.ui.theme.IndigoGlow
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeClockCard(
    nextAlarmSummary: String?,
    onClick: () -> Unit,
    weatherState: WeatherUiState = WeatherUiState(),
    onWeatherClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val amPmFmt = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }

    val nowDate = Date(nowMillis)
    val timeText = timeFmt.format(nowDate)
    val amPmText = if (is24Hour) "" else amPmFmt.format(nowDate)
    val dateText = dateFmt.format(nowDate)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_clock_card_container"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderHairline),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            CyanPulse.copy(alpha = 0.10f),
                            MidnightSurface,
                            IndigoGlow.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clock + Date + Next Alarm (tap to set quick alarm or open Clock)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 2.dp)
                        .testTag("home_clock_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 40.sp,
                                lineHeight = 44.sp,
                                letterSpacing = (-1.0).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            modifier = Modifier.testTag("home_clock_time")
                        )
                        if (amPmText.isNotEmpty()) {
                            Text(
                                text = amPmText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = CyanPulse,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_clock_date")
                    )

                    Spacer(Modifier.height(2.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (nextAlarmSummary != null) AmberSignalContainer else HighSurface,
                        border = BorderStroke(
                            1.dp,
                            if (nextAlarmSummary != null) AmberSignal.copy(alpha = 0.45f) else BorderHairline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (nextAlarmSummary != null) {
                                    Icons.Filled.AccessAlarm
                                } else {
                                    Icons.Outlined.AlarmAdd
                                },
                                contentDescription = null,
                                tint = if (nextAlarmSummary != null) AmberSignal else CyanPulse,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (nextAlarmSummary != null) {
                                    stringResource(R.string.clock_next_alarm_prefix, nextAlarmSummary)
                                } else {
                                    stringResource(R.string.clock_tap_hint)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (nextAlarmSummary != null) AmberSignal else CyanPulse,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("home_clock_alarm_pill")
                            )
                        }
                    }
                }

                // Weather Area inside Clock Card (tap to view/configure weather & add/open weather app shortcut)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElevatedSurface.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        if (weatherState.hasWeatherData || weatherState.shortcutApp != null) {
                            CyanPulse.copy(alpha = 0.35f)
                        } else {
                            BorderHairline
                        }
                    ),
                    modifier = Modifier
                        .widthIn(min = 112.dp, max = 148.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onWeatherClick)
                        .testTag("home_clock_weather_area")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (weatherState.hasWeatherData) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WeatherConditionIcon(
                                    kind = weatherState.conditionKind,
                                    size = 22.dp
                                )
                                Text(
                                    text = "${weatherState.displayTemp}${weatherState.unitSuffix}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("home_clock_weather_temp")
                                )
                            }
                            Text(
                                text = weatherState.conditionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPulse,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subLabel = weatherState.locationName.substringBefore(",")
                                .ifBlank { weatherState.shortcutApp?.label.orEmpty() }
                            if (subLabel.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = subLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (weatherState.shortcutApp != null) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = CyanPulse,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            WeatherConditionIcon(
                                kind = weatherState.conditionKind,
                                size = 24.dp
                            )
                            Text(
                                text = if (weatherState.isLoading) {
                                    stringResource(R.string.weather_loading)
                                } else {
                                    stringResource(R.string.weather_add_cta)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = CyanPulse,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("home_clock_weather_cta")
                            )
                            Text(
                                text = weatherState.shortcutApp?.label
                                    ?: stringResource(R.string.weather_tap_configure),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAlarmDialog(
    onSetAlarm: (hour: Int, minute: Int, label: String?) -> Unit,
    onOpenClockApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }

    val initialCal = remember {
        Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
    }
    var selectedHour by rememberSaveable { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
    var alarmLabel by rememberSaveable { mutableStateOf("") }

    val formattedTime = remember(selectedHour, selectedMinute, is24Hour) {
        ClockAlarmHelper.formatAlarmTime(selectedHour, selectedMinute, is24Hour)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .testTag("quick_alarm_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, BorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessAlarm,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.alarm_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("alarm_dialog_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.dock_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.alarm_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Crisp digital time adjuster that adapts to both Portrait and Landscape
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 36.sp
                            ),
                            color = CyanPulse,
                            modifier = Modifier.testTag("alarm_selected_time_text")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeStepperControl(
                                label = "Hour",
                                valueText = String.format(
                                    Locale.getDefault(),
                                    "%02d",
                                    if (is24Hour) selectedHour else {
                                        val h12 = selectedHour % 12
                                        if (h12 == 0) 12 else h12
                                    }
                                ),
                                onDecrement = { selectedHour = (selectedHour + 23) % 24 },
                                onIncrement = { selectedHour = (selectedHour + 1) % 24 },
                                testTagPrefix = "alarm_hour"
                            )

                            TimeStepperControl(
                                label = "Min",
                                valueText = String.format(Locale.getDefault(), "%02d", selectedMinute),
                                onDecrement = { selectedMinute = (selectedMinute + 59) % 60 },
                                onIncrement = { selectedMinute = (selectedMinute + 1) % 60 },
                                testTagPrefix = "alarm_minute"
                            )

                            if (!is24Hour) {
                                val isPm = selectedHour >= 12
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Period",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { selectedHour = (selectedHour + 12) % 24 },
                                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("alarm_ampm_toggle")
                                    ) {
                                        Text(
                                            text = if (isPm) "PM" else "AM",
                                            color = CyanPulse,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick alarm presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
                            selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                            selectedMinute = cal.get(Calendar.MINUTE)
                        },
                        label = { Text(stringResource(R.string.alarm_preset_15m)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = HighSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("alarm_preset_15m")
                    )
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
                            selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                            selectedMinute = cal.get(Calendar.MINUTE)
                        },
                        label = { Text(stringResource(R.string.alarm_preset_30m)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = HighSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("alarm_preset_30m")
                    )
                    AssistChip(
                        onClick = {
                            selectedHour = 7
                            selectedMinute = 0
                        },
                        label = { Text(stringResource(R.string.alarm_preset_7am)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = HighSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("alarm_preset_7am")
                    )
                    AssistChip(
                        onClick = {
                            selectedHour = 8
                            selectedMinute = 0
                        },
                        label = { Text(stringResource(R.string.alarm_preset_8am)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = HighSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("alarm_preset_8am")
                    )
                }

                OutlinedTextField(
                    value = alarmLabel,
                    onValueChange = { alarmLabel = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input"),
                    placeholder = { Text(stringResource(R.string.alarm_label_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ElevatedSurface,
                        unfocusedContainerColor = ElevatedSurface,
                        focusedBorderColor = CyanPulse,
                        unfocusedBorderColor = BorderHairline
                    )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onSetAlarm(selectedHour, selectedMinute, alarmLabel.takeIf { it.isNotBlank() })
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("alarm_confirm_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessAlarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.alarm_action_set)} ($formattedTime)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onOpenClockApp()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("alarm_open_clock_button"),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.alarm_action_open_clock))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeStepperControl(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTagPrefix: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_dec")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_inc")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun HomeMusicCard(
    mediaState: MediaPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    preferredMusicApp: LauncherAppInfo? = null,
    onChooseMusicService: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = if (mediaState.isActive && mediaState.isPlaying) EmeraldOk else CyanPulse

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_music_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (mediaState.isActive) accent.copy(alpha = 0.42f) else BorderHairline
        ),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        if (mediaState.isActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.12f),
                                MidnightSurface
                            )
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenMusicApp)
                        .testTag("music_open_app_area"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaState.artworkBitmap != null) {
                            Image(
                                bitmap = mediaState.artworkBitmap,
                                contentDescription = stringResource(
                                    R.string.music_artwork_desc,
                                    mediaState.title
                                ),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Album,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(28.dp)
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (mediaState.isPlaying) EmeraldOkContainer else HighSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (mediaState.isPlaying) {
                                            stringResource(R.string.music_now_playing)
                                        } else {
                                            stringResource(R.string.music_paused)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                            if (mediaState.appLabel.isNotBlank()) {
                                Text(
                                    text = mediaState.appLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = mediaState.title.ifBlank { stringResource(R.string.music_idle_title) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("music_track_title")
                        )

                        if (mediaState.artist.isNotBlank()) {
                            Text(
                                text = mediaState.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("music_track_artist")
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.music_action_open_app),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )

                    IconButton(
                        onClick = onChooseMusicService,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_choose_service_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.music_service_choose_desc),
                            tint = CyanPulse,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        enabled = mediaState.canSkipPrevious,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.music_action_previous),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = CyanPulse,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlayPause)
                            .testTag("music_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (mediaState.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (mediaState.isPlaying) {
                                    stringResource(R.string.music_action_pause)
                                } else {
                                    stringResource(R.string.music_action_play)
                                },
                                tint = ObsidianBackground,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSkipNext,
                        enabled = mediaState.canSkipNext,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.music_action_next),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMusicApp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("music_idle_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (preferredMusicApp != null) {
                    AppIconBadge(
                        packageName = preferredMusicApp.packageName,
                        label = preferredMusicApp.label,
                        size = 44.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HighSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = preferredMusicApp?.label ?: stringResource(R.string.music_idle_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("music_idle_title_text")
                    )
                    Text(
                        text = when {
                            preferredMusicApp != null && mediaState.hasSessionAccess -> {
                                stringResource(R.string.music_service_connected_subtitle, preferredMusicApp.label)
                            }
                            preferredMusicApp != null -> {
                                stringResource(R.string.music_service_connected_need_access, preferredMusicApp.label)
                            }
                            mediaState.hasSessionAccess -> {
                                stringResource(R.string.music_idle_subtitle)
                            }
                            else -> {
                                stringResource(R.string.music_access_needed)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onChooseMusicService,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("music_choose_service_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.music_service_choose_desc),
                        tint = CyanPulse,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!mediaState.hasSessionAccess) {
                    IconButton(
                        onClick = onGrantNotificationAccess,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_grant_access_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = stringResource(R.string.banner_grant_access),
                            tint = AmberSignal
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenMusicApp,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("music_open_app_button")
                    ) {
                        Text(
                            text = stringResource(R.string.music_action_open_app),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

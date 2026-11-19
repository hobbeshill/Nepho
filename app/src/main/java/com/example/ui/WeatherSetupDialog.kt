package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.launcher.WeatherConditionKind
import com.example.launcher.WeatherLocationOption
import com.example.launcher.WeatherService
import com.example.launcher.WeatherUiState
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighSurface
import com.example.ui.theme.IndigoGlow
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun WeatherConditionIcon(
    kind: WeatherConditionKind,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val (vector: ImageVector, tint: Color) = when (kind) {
        WeatherConditionKind.CLEAR_DAY -> Icons.Filled.WbSunny to AmberSignal
        WeatherConditionKind.CLEAR_NIGHT -> Icons.Filled.NightsStay to IndigoGlow
        WeatherConditionKind.PARTLY_CLOUDY -> Icons.Filled.FilterDrama to CyanPulse
        WeatherConditionKind.CLOUDY -> Icons.Filled.Cloud to MaterialTheme.colorScheme.onSurfaceVariant
        WeatherConditionKind.FOG -> Icons.Filled.Cloud to MaterialTheme.colorScheme.onSurfaceVariant
        WeatherConditionKind.RAIN -> Icons.Filled.WaterDrop to CyanPulse
        WeatherConditionKind.SNOW -> Icons.Filled.AcUnit to MaterialTheme.colorScheme.onBackground
        WeatherConditionKind.THUNDERSTORM -> Icons.Filled.Thunderstorm to AmberSignal
    }
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun WeatherSetupDialog(
    weatherState: WeatherUiState,
    installedApps: List<LauncherAppInfo>,
    citySearchResults: List<WeatherLocationOption>,
    isSearchingCities: Boolean,
    onSearchCities: (String) -> Unit,
    onSelectLocation: (WeatherLocationOption) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onToggleUnit: () -> Unit,
    onOpenShortcutApp: () -> Unit,
    onSelectShortcutApp: (LauncherAppInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var appPickerQuery by rememberSaveable { mutableStateOf("") }

    val prioritizedApps = remember(installedApps) {
        WeatherService.prioritizeWeatherApps(installedApps)
    }
    val filteredPickerApps = remember(prioritizedApps, appPickerQuery) {
        if (appPickerQuery.isBlank()) prioritizedApps
        else LauncherAppCatalog.filterApps(prioritizedApps, appPickerQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .testTag("weather_setup_dialog"),
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherConditionIcon(kind = weatherState.conditionKind, size = 24.dp)
                        Text(
                            text = stringResource(R.string.weather_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onToggleUnit,
                            modifier = Modifier.testTag("weather_unit_toggle")
                        ) {
                            Text(
                                text = if (weatherState.useFahrenheit) "°F" else "°C",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanPulse
                            )
                        }
                        if (weatherState.latitude != null && weatherState.longitude != null) {
                            IconButton(
                                onClick = onRefreshWeather,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("weather_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.weather_refresh),
                                    tint = CyanPulse
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("weather_dialog_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.dock_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Current weather snapshot summary card
                if (weatherState.hasWeatherData) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ElevatedSurface,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${weatherState.displayTemp}${weatherState.unitSuffix}",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 34.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.testTag("weather_dialog_temp")
                                )
                                Text(
                                    text = weatherState.conditionLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CyanPulse
                                )
                                if (weatherState.locationName.isNotBlank()) {
                                    Text(
                                        text = weatherState.locationName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WeatherConditionIcon(kind = weatherState.conditionKind, size = 34.dp)
                                val hi = weatherState.displayHigh
                                val lo = weatherState.displayLow
                                if (hi != null && lo != null) {
                                    Text(
                                        text = stringResource(R.string.weather_high_low, hi, lo),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Weather App Shortcut section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.weather_shortcut_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanPulse
                    )

                    val shortcut = weatherState.shortcutApp
                    if (shortcut != null) {
                        Button(
                            onClick = {
                                onOpenShortcutApp()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("weather_open_app_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.weather_open_app, shortcut.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAppPicker = !showAppPicker },
                                border = BorderStroke(1.dp, BorderHairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("weather_choose_app_button")
                            ) {
                                Text(stringResource(R.string.weather_change_app_shortcut))
                            }
                            OutlinedButton(
                                onClick = {
                                    onSelectShortcutApp(null)
                                    showAppPicker = false
                                },
                                border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("weather_clear_app_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.weather_remove_app_shortcut),
                                    color = CrimsonError
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { showAppPicker = !showAppPicker },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("weather_choose_app_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulseContainer,
                                contentColor = CyanPulse
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.weather_add_app_shortcut))
                        }
                    }

                    if (showAppPicker) {
                        OutlinedTextField(
                            value = appPickerQuery,
                            onValueChange = { appPickerQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weather_app_picker_search"),
                            placeholder = { Text(stringResource(R.string.weather_picker_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ElevatedSurface,
                                unfocusedContainerColor = ElevatedSurface,
                                focusedBorderColor = CyanPulse,
                                unfocusedBorderColor = BorderHairline
                            )
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = filteredPickerApps,
                                key = { "weather_pick_${it.packageName}" }
                            ) { app ->
                                val isSelected = shortcut?.packageName == app.packageName
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) CyanPulseContainer.copy(alpha = 0.45f) else ElevatedSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) CyanPulse.copy(alpha = 0.5f) else BorderHairline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelectShortcutApp(app)
                                            showAppPicker = false
                                        }
                                        .testTag("weather_app_option_${app.packageName}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppIconBadge(app.packageName, app.label, size = 34.dp)
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = CyanPulse
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderHairline)

                // Location setup section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.weather_location_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanPulse
                    )

                    OutlinedButton(
                        onClick = onUseCurrentLocation,
                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("weather_use_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.weather_use_my_location),
                            color = CyanPulse
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cityQuery,
                            onValueChange = { cityQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("weather_city_input"),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.weather_city_hint),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ElevatedSurface,
                                unfocusedContainerColor = ElevatedSurface,
                                focusedBorderColor = CyanPulse,
                                unfocusedBorderColor = BorderHairline
                            )
                        )
                        Button(
                            onClick = { onSearchCities(cityQuery) },
                            enabled = cityQuery.isNotBlank() && !isSearchingCities,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HighSurface,
                                contentColor = CyanPulse
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("weather_city_search_button")
                        ) {
                            if (isSearchingCities) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanPulse
                                )
                            } else {
                                Text(stringResource(R.string.weather_search_action))
                            }
                        }
                    }

                    if (citySearchResults.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            citySearchResults.forEachIndexed { index, option ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ElevatedSurface,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectLocation(option) }
                                        .testTag("weather_city_result_$index")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = CyanPulse,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
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
        }
    }
}

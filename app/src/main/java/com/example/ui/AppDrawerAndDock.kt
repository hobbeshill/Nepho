package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighSurface
import com.example.ui.theme.IndigoGlow
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HomeSectionTarget {
    CLOCK,
    MUSIC,
    CALENDAR,
    FEED,
    VAULT,
    APPS
}

@Composable
fun AppIconBadge(
    packageName: String,
    label: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            LauncherAppCatalog.loadAppIconBitmap(context, packageName)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(HighSurface),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = label.trim().take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = CyanPulse,
                maxLines = 1
            )
        }
    }
}

fun LazyListScope.appDrawerSectionItems(
    totalInstalledCount: Int,
    searchQuery: String,
    groupedApps: Map<String, List<LauncherAppInfo>>,
    pinnedPackages: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onJumpToLetter: (String) -> Unit,
    onLaunchApp: (LauncherAppInfo) -> Unit,
    onTogglePin: (LauncherAppInfo) -> Unit
) {
    item(key = "app_drawer_header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = CyanPulse,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.drawer_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HighSurface,
                    border = BorderStroke(1.dp, BorderHairline)
                ) {
                    Text(
                        text = stringResource(R.string.drawer_app_count, totalInstalledCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_drawer_search_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.drawer_search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.drawer_search_hint)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_app_drawer_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.drawer_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MidnightSurface,
                    unfocusedContainerColor = MidnightSurface,
                    focusedBorderColor = CyanPulse,
                    unfocusedBorderColor = BorderHairline
                )
            )

            val letters = groupedApps.keys.toList()
            if (letters.isNotEmpty() && searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    letters.forEach { letter ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MidnightSurface,
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJumpToLetter(letter) }
                                .testTag("drawer_letter_$letter")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyanPulse
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (groupedApps.isEmpty()) {
        item(key = "app_drawer_empty") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_drawer_empty_card"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderHairline),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface)
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.drawer_empty_search, searchQuery.trim())
                    } else {
                        stringResource(R.string.drawer_empty_installed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    } else {
        groupedApps.forEach { (letter, appsInSection) ->
            item(key = "drawer_section_$letter") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanPulseContainer
                    ) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyanPulse,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = BorderHairline
                    )
                }
            }

            items(items = appsInSection, key = { "drawer_app_${it.packageName}" }) { app ->
                val isPinned = app.packageName in pinnedPackages
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isPinned) CyanPulse.copy(alpha = 0.28f) else BorderHairline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onLaunchApp(app) }
                        .testTag("drawer_app_row_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconBadge(
                            packageName = app.packageName,
                            label = app.label,
                            size = 40.dp
                        )

                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onTogglePin(app) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("drawer_pin_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) {
                                    stringResource(R.string.drawer_unpin_shortcut)
                                } else {
                                    stringResource(R.string.drawer_pin_shortcut)
                                },
                                tint = if (isPinned) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomShortcutDock(
    pinnedApps: List<LauncherAppInfo>,
    onLaunchApp: (LauncherAppInfo) -> Unit,
    onExpandDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleDockApps = remember(pinnedApps) {
        pinnedApps.take(LauncherAppCatalog.MAX_DOCK_VISIBLE)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_shortcut_dock"),
        color = MidnightSurface,
        border = BorderStroke(1.dp, BorderHairline),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (visibleDockApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.dock_expand_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpandDrawer)
                        .padding(horizontal = 8.dp)
                )
            } else {
                visibleDockApps.forEach { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunchApp(app) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("dock_shortcut_${app.packageName}")
                    ) {
                        AppIconBadge(
                            packageName = app.packageName,
                            label = app.label,
                            size = 38.dp
                        )
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CyanPulseContainer,
                border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.45f)),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onExpandDrawer)
                    .testTag("dock_expand_button")
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.dock_expand_label),
                        tint = CyanPulse,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShortcutDrawerSheet(
    pinnedApps: List<LauncherAppInfo>,
    allInstalledApps: List<LauncherAppInfo>,
    isDefaultHome: Boolean,
    unreadCount: Int,
    linksCount: Int,
    onLaunchApp: (LauncherAppInfo) -> Unit,
    onTogglePin: (LauncherAppInfo) -> Unit,
    onMovePinnedShortcut: (packageName: String, delta: Int) -> Unit,
    onJumpToSection: (HomeSectionTarget) -> Unit,
    onOpenDefaultHomeSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var showAddPicker by rememberSaveable { mutableStateOf(false) }
    var pickerQuery by rememberSaveable { mutableStateOf("") }

    val pinnedSet = remember(pinnedApps) { pinnedApps.map { it.packageName }.toSet() }
    val filteredPickerApps = remember(allInstalledApps, pickerQuery) {
        LauncherAppCatalog.filterApps(allInstalledApps, pickerQuery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("shortcut_drawer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dock_drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("shortcut_drawer_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dock_close)
                    )
                }
            }

            // Quick section navigation pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionJumpChip(
                    label = stringResource(R.string.dock_jump_clock),
                    icon = Icons.Filled.AccessTime,
                    tag = "drawer_jump_clock",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.CLOCK)
                        onDismiss()
                    }
                )
                SectionJumpChip(
                    label = stringResource(R.string.dock_jump_music),
                    icon = Icons.Filled.MusicNote,
                    tag = "drawer_jump_music",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.MUSIC)
                        onDismiss()
                    }
                )
                SectionJumpChip(
                    label = stringResource(R.string.dock_jump_calendar),
                    icon = Icons.Filled.CalendarToday,
                    tag = "drawer_jump_calendar",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.CALENDAR)
                        onDismiss()
                    }
                )
                SectionJumpChip(
                    label = if (unreadCount > 0) {
                        "${stringResource(R.string.dock_jump_feed)} ($unreadCount)"
                    } else {
                        stringResource(R.string.dock_jump_feed)
                    },
                    icon = Icons.Filled.DynamicFeed,
                    tag = "drawer_jump_feed",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.FEED)
                        onDismiss()
                    }
                )
                SectionJumpChip(
                    label = if (linksCount > 0) {
                        "${stringResource(R.string.dock_jump_vault)} ($linksCount)"
                    } else {
                        stringResource(R.string.dock_jump_vault)
                    },
                    icon = Icons.Filled.Link,
                    tag = "drawer_jump_vault",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.VAULT)
                        onDismiss()
                    }
                )
                SectionJumpChip(
                    label = stringResource(R.string.dock_jump_apps),
                    icon = Icons.Filled.Apps,
                    tag = "drawer_jump_apps",
                    onClick = {
                        onJumpToSection(HomeSectionTarget.APPS)
                        onDismiss()
                    }
                )
            }

            // Default Home promotion banner when Nepho isn't yet default
            if (!isDefaultHome) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, IndigoGlow.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = null,
                            tint = IndigoGlow,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.default_home_banner_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.default_home_banner_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                onOpenDefaultHomeSettings()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianBackground
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("drawer_set_default_home_button")
                        ) {
                            Text(
                                text = "Set",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderHairline)

            // Pinned Favorites header + Edit / Add controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pinned (${pinnedApps.size}/${LauncherAppCatalog.MAX_PINNED_SHORTCUTS})",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanPulse
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            isEditing = !isEditing
                            if (isEditing) showAddPicker = false
                        },
                        modifier = Modifier.testTag("drawer_edit_shortcuts_button")
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isEditing) {
                                stringResource(R.string.dock_done_editing)
                            } else {
                                stringResource(R.string.dock_edit_shortcuts)
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            showAddPicker = !showAddPicker
                            if (showAddPicker) isEditing = false
                        },
                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("drawer_add_shortcut_button")
                    ) {
                        Icon(
                            imageVector = if (showAddPicker) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dock_add_shortcut))
                    }
                }
            }

            if (showAddPicker) {
                OutlinedTextField(
                    value = pickerQuery,
                    onValueChange = { pickerQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dock_picker_search_input"),
                    placeholder = { Text(stringResource(R.string.dock_picker_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = filteredPickerApps, key = { "picker_${it.packageName}" }) { app ->
                        val isPinned = app.packageName in pinnedSet
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPinned) CyanPulseContainer.copy(alpha = 0.45f) else ElevatedSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isPinned) CyanPulse.copy(alpha = 0.5f) else BorderHairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onTogglePin(app) }
                                .testTag("dock_picker_item_${app.packageName}")
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
                                Icon(
                                    imageVector = if (isPinned) Icons.Filled.Check else Icons.Filled.Add,
                                    contentDescription = if (isPinned) {
                                        stringResource(R.string.drawer_unpin_shortcut)
                                    } else {
                                        stringResource(R.string.drawer_pin_shortcut)
                                    },
                                    tint = if (isPinned) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (pinnedApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.dock_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 4
                ) {
                    pinnedApps.forEachIndexed { index, app ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ElevatedSurface,
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .width(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !isEditing) {
                                    onLaunchApp(app)
                                    onDismiss()
                                }
                                .testTag("sheet_shortcut_${app.packageName}")
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AppIconBadge(app.packageName, app.label, size = 42.dp)
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (isEditing) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onMovePinnedShortcut(app.packageName, -1) },
                                            enabled = index > 0,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("sheet_move_left_${app.packageName}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                contentDescription = stringResource(R.string.dock_move_left),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onTogglePin(app) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("sheet_remove_${app.packageName}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.dock_remove_shortcut),
                                                tint = CrimsonError,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onMovePinnedShortcut(app.packageName, 1) },
                                            enabled = index < pinnedApps.lastIndex,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("sheet_move_right_${app.packageName}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = stringResource(R.string.dock_move_right),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionJumpChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanPulse,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = HighSurface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.testTag(tag)
    )
}

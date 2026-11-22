package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.ui.theme.LocalCalmVisualPrefs
import com.example.ui.theme.LocalNephoPalette
import kotlinx.coroutines.launch

/**
 * Computes the LazyColumn item index of a letter section header inside an alphabetically
 * grouped app list (header = 1 item, followed by each app in that section).
 */
internal fun computeLetterOffsetInDrawer(
    groupedApps: Map<String, List<LauncherAppInfo>>,
    targetLetter: String
): Int {
    var offset = 0
    for ((letter, apps) in groupedApps) {
        if (letter == targetLetter) {
            return offset
        }
        offset += 1 + apps.size
    }
    return -1
}

/**
 * Full-height alphabetical A–Z app drawer sheet opened when swiping up from the bottom of the
 * home screen (or swiping up when the home screen cannot scroll further).
 * Keeps the search field and A–Z letter jump bar pinned at the top while the grouped app list
 * scrolls underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphabeticalAppDrawerSheet(
    installedApps: List<LauncherAppInfo>,
    pinnedPackages: Set<String>,
    onLaunchApp: (LauncherAppInfo) -> Unit,
    onTogglePin: (LauncherAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNephoPalette.current
    val calmPrefs = LocalCalmVisualPrefs.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(installedApps, searchQuery) {
        LauncherAppCatalog.filterApps(installedApps, searchQuery)
    }
    val groupedApps = remember(filteredApps) {
        LauncherAppCatalog.groupAlphabetically(filteredApps)
    }

    ModalBottomSheet(
        onDismissRequest = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("alphabetical_app_drawer_sheet")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row
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
                            tint = palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.drawer_alphabetical_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!calmPrefs.hideUnreadCounts) {
                            val countShape = RoundedCornerShape(8.dp)
                            Surface(
                                shape = countShape,
                                color = palette.highSurface,
                                modifier = Modifier
                                    .clip(countShape)
                                    .calmingLitBorder(
                                        accentColor = palette.accent,
                                        cornerRadius = 8.dp,
                                        strokeWidth = 1.dp,
                                        intensity = CalmBorderIntensity.SUBTLE
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.drawer_app_count, installedApps.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onDismiss()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("alphabetical_drawer_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.drawer_close_desc)
                        )
                    }
                }

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alphabetical_drawer_search_input"),
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
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("alphabetical_drawer_clear_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.drawer_clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.elevatedSurface,
                        unfocusedContainerColor = palette.elevatedSurface,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    )
                )

                // Sticky A-Z jump strip
                val letters = groupedApps.keys.toList()
                if (letters.isNotEmpty() && searchQuery.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        letters.forEach { letter ->
                            val letterShape = RoundedCornerShape(10.dp)
                            Surface(
                                shape = letterShape,
                                color = palette.elevatedSurface,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(letterShape)
                                    .calmingLitBorder(
                                        accentColor = palette.accent,
                                        cornerRadius = 10.dp,
                                        strokeWidth = 1.dp,
                                        intensity = CalmBorderIntensity.SUBTLE
                                    )
                                    .clickable {
                                        val offset = computeLetterOffsetInDrawer(groupedApps, letter)
                                        if (offset >= 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(offset)
                                            }
                                        }
                                    }
                                    .testTag("alphabetical_drawer_letter_$letter")
                            ) {
                                Box(
                                    modifier = Modifier.size(width = 38.dp, height = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = palette.accent
                                    )
                                }
                            }
                        }
                    }
                }

                // Alphabetical grouped app list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("alphabetical_drawer_list"),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (groupedApps.isEmpty()) {
                        item(key = "sheet_drawer_empty") {
                            val emptyShape = RoundedCornerShape(16.dp)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(emptyShape)
                                    .calmingLitBorder(
                                        accentColor = palette.accent,
                                        cornerRadius = 16.dp,
                                        strokeWidth = 1.1.dp,
                                        intensity = CalmBorderIntensity.AMBIENT
                                    )
                                    .testTag("alphabetical_drawer_empty_card"),
                                shape = emptyShape,
                                colors = CardDefaults.cardColors(containerColor = palette.elevatedSurface)
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
                            item(key = "sheet_drawer_section_$letter") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                        .testTag("alphabetical_drawer_section_$letter"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = palette.accentContainer
                                    ) {
                                        Text(
                                            text = letter,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = palette.accent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = palette.border
                                    )
                                }
                            }

                            items(
                                items = appsInSection,
                                key = { "sheet_drawer_app_${it.packageName}" }
                            ) { app ->
                                val isPinned = app.packageName in pinnedPackages
                                val rowShape = RoundedCornerShape(14.dp)
                                Surface(
                                    shape = rowShape,
                                    color = palette.elevatedSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(rowShape)
                                        .calmingLitBorder(
                                            accentColor = if (isPinned) palette.calmBlue else palette.accent,
                                            cornerRadius = 14.dp,
                                            strokeWidth = 1.dp,
                                            intensity = if (isPinned) CalmBorderIntensity.AMBIENT else CalmBorderIntensity.SUBTLE
                                        )
                                        .clickable {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onLaunchApp(app)
                                            onDismiss()
                                        }
                                        .testTag("alphabetical_drawer_app_${app.packageName}")
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
                                                .testTag("alphabetical_drawer_pin_${app.packageName}")
                                        ) {
                                            Icon(
                                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (isPinned) {
                                                    stringResource(R.string.drawer_unpin_shortcut)
                                                } else {
                                                    stringResource(R.string.drawer_pin_shortcut)
                                                },
                                                tint = if (isPinned) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(19.dp)
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
}

/**
 * Minimal calm bottom-edge swipe handle shown when the Bottom Shortcut Dock is hidden,
 * providing a clear bottom swipe-up (and tap) zone to open the alphabetical app drawer.
 */
@Composable
fun BottomEdgeAppDrawerHandle(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNephoPalette.current
    val accessibilityLabel = stringResource(R.string.bottom_edge_open_apps_desc)

    Surface(
        color = palette.background,
        modifier = modifier
            .fillMaxWidth()
            .calmingLitBorder(
                accentColor = palette.accent,
                strokeWidth = 1.dp,
                intensity = CalmBorderIntensity.SUBTLE,
                topEdgeOnly = true
            )
            .swipeUpGestureOverlay(onSwipeUp = onOpenDrawer)
            .testTag("bottom_drawer_swipe_handle")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = accessibilityLabel }
                .clickable(onClick = onOpenDrawer)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.55f))
            )
        }
    }
}

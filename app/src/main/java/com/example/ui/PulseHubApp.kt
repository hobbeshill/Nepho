package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NephoApp(
    viewModel: PulseViewModel,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val sessionOpenedAt by viewModel.sessionOpenedAt.collectAsStateWithLifecycle()
    val bannerDismissed by viewModel.bannerDismissed.collectAsStateWithLifecycle()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val defaultShadeMode by viewModel.defaultShadeMode.collectAsStateWithLifecycle()
    val shadePreferenceItems by viewModel.shadePreferenceItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val vaultSearchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
    val selectedAppFilter by viewModel.selectedAppFilter.collectAsStateWithLifecycle()
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val hasLinksOnly by viewModel.hasLinksOnly.collectAsStateWithLifecycle()
    val starredOnly by viewModel.starredOnly.collectAsStateWithLifecycle()
    val notifications by viewModel.filteredNotifications.collectAsStateWithLifecycle()
    val links by viewModel.activeLinks.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val digestState by viewModel.digestState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var showShadePrefsDialog by remember { mutableStateOf(false) }
    var reminderTargetItem by remember { mutableStateOf<com.example.data.PulseNotificationItem?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showComposeDialog) {
        ComposeCaptureDialog(
            onDismiss = { showComposeDialog = false },
            onSubmit = { app, title, body ->
                viewModel.ingestSharedOrCustomMessage(
                    app = app,
                    title = title,
                    body = body,
                    sourceChannel = "MANUAL_CAPTURE"
                )
            }
        )
    }

    if (showShadePrefsDialog) {
        ShadePreferencesDialog(
            defaultShadeMode = defaultShadeMode,
            appPreferences = shadePreferenceItems,
            hasNotificationPermission = hasNotificationPermission,
            onSetDefaultShadeMode = viewModel::setDefaultShadeMode,
            onSetAppOptedIn = viewModel::setAppOptedIn,
            onSetAppShadeMode = viewModel::setAppShadeMode,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onDismiss = { showShadePrefsDialog = false }
        )
    }

    reminderTargetItem?.let { target ->
        SetReminderDialog(
            item = target,
            onDismiss = { reminderTargetItem = null },
            onSchedule = { triggerAt, note ->
                viewModel.scheduleReminder(target.key, triggerAt, note)
            },
            onClearReminder = {
                viewModel.clearReminder(target.key)
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.background(ObsidianBackground)) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ObsidianBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldOk)
                                    )
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                val sinceText = timeFmt.format(Date(sessionOpenedAt))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MidnightSurface,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier.testTag("session_status_pill")
                                ) {
                                    Text(
                                        text = stringResource(R.string.status_window_pill, sinceText),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPulse,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showShadePrefsDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("shade_preferences_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = stringResource(R.string.action_shade_preferences),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.resetSession() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reset_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = stringResource(R.string.action_reset_session),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("simulate_incoming_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_simulate_incoming)
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_shade_preferences)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Tune, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showShadePrefsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_shade_preferences")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_simulate_batch)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.simulateIncomingBatch(includeExpiredTest = false)
                                        },
                                        modifier = Modifier.testTag("menu_simulate_batch")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_simulate_expiry)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.simulateIncomingBatch(includeExpiredTest = true)
                                        },
                                        modifier = Modifier.testTag("menu_simulate_expiry")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_compose_capture)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.EditNote, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showComposeDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_compose_capture")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_reset_session)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.resetSession()
                                        },
                                        modifier = Modifier.testTag("menu_reset_session")
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MidnightSurface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        PulseTabItem(
                            selected = selectedTab == PulseTab.FEED,
                            label = stringResource(R.string.tab_feed),
                            badgeCount = metrics.unreadTotal,
                            selectedIcon = Icons.Default.DynamicFeed,
                            unselectedIcon = Icons.Outlined.DynamicFeed,
                            testTag = "nav_tab_feed",
                            onClick = { viewModel.selectTab(PulseTab.FEED) }
                        )
                        PulseTabItem(
                            selected = selectedTab == PulseTab.SMART_DIGEST,
                            label = stringResource(R.string.tab_digest),
                            badgeCount = 0,
                            selectedIcon = Icons.Default.AutoAwesome,
                            unselectedIcon = Icons.Outlined.AutoAwesome,
                            testTag = "nav_tab_digest",
                            onClick = { viewModel.selectTab(PulseTab.SMART_DIGEST) }
                        )
                        PulseTabItem(
                            selected = selectedTab == PulseTab.LINKS_VAULT,
                            label = stringResource(R.string.tab_vault),
                            badgeCount = metrics.totalLinks,
                            selectedIcon = Icons.Default.Link,
                            unselectedIcon = Icons.Outlined.Link,
                            testTag = "nav_tab_vault",
                            onClick = { viewModel.selectTab(PulseTab.LINKS_VAULT) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MidnightSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.size(12.dp))
                        NavigationRailItem(
                            selected = selectedTab == PulseTab.FEED,
                            onClick = { viewModel.selectTab(PulseTab.FEED) },
                            icon = {
                                BadgedNavIcon(
                                    icon = if (selectedTab == PulseTab.FEED) Icons.Default.DynamicFeed else Icons.Outlined.DynamicFeed,
                                    count = metrics.unreadTotal,
                                    contentDescription = stringResource(R.string.tab_feed)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_feed)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanPulse,
                                indicatorColor = CyanPulseContainer
                            ),
                            modifier = Modifier.testTag("rail_tab_feed")
                        )
                        NavigationRailItem(
                            selected = selectedTab == PulseTab.SMART_DIGEST,
                            onClick = { viewModel.selectTab(PulseTab.SMART_DIGEST) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == PulseTab.SMART_DIGEST) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = stringResource(R.string.tab_digest)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_digest)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanPulse,
                                indicatorColor = CyanPulseContainer
                            ),
                            modifier = Modifier.testTag("rail_tab_digest")
                        )
                        NavigationRailItem(
                            selected = selectedTab == PulseTab.LINKS_VAULT,
                            onClick = { viewModel.selectTab(PulseTab.LINKS_VAULT) },
                            icon = {
                                BadgedNavIcon(
                                    icon = if (selectedTab == PulseTab.LINKS_VAULT) Icons.Default.Link else Icons.Outlined.Link,
                                    count = metrics.totalLinks,
                                    contentDescription = stringResource(R.string.tab_vault)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_vault)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanPulse,
                                indicatorColor = CyanPulseContainer
                            ),
                            modifier = Modifier.testTag("rail_tab_vault")
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            PulseTab.FEED -> {
                                FeedTab(
                                    notifications = notifications,
                                    sessionOpenedAt = sessionOpenedAt,
                                    searchQuery = searchQuery,
                                    selectedAppFilter = selectedAppFilter,
                                    unreadOnly = unreadOnly,
                                    hasLinksOnly = hasLinksOnly,
                                    starredOnly = starredOnly,
                                    bannerDismissed = bannerDismissed,
                                    hasNotificationPermission = hasNotificationPermission,
                                    isNotificationAccessGranted = isNotificationAccessGranted,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onSelectAppFilter = viewModel::selectAppFilter,
                                    onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                                    onToggleHasLinksOnly = viewModel::toggleHasLinksOnly,
                                    onToggleStarredOnly = viewModel::toggleStarredOnly,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                                    onOpenShadePreferences = { showShadePrefsDialog = true },
                                    onSimulateIncoming = { viewModel.simulateIncomingBatch(false) },
                                    onDismissBanner = viewModel::dismissBanner,
                                    onMarkAsRead = viewModel::markAsRead,
                                    onToggleStar = viewModel::toggleNotificationStar,
                                    onOpenReminderDialog = { item -> reminderTargetItem = item },
                                    onSendReply = viewModel::sendInlineReply,
                                    onDismissItem = viewModel::dismissNotification,
                                    onSecondaryAction = viewModel::executeSecondaryAction,
                                    onOpenVaultTab = { viewModel.selectTab(PulseTab.LINKS_VAULT) }
                                )
                            }

                            PulseTab.SMART_DIGEST -> {
                                SmartDigestTab(
                                    metrics = metrics,
                                    digestState = digestState,
                                    onGenerateAiSummary = viewModel::generateAiSummary,
                                    onGenerateLocalFallback = viewModel::generateLocalFallbackSummary
                                )
                            }

                            PulseTab.LINKS_VAULT -> {
                                LinksVaultTab(
                                    links = links,
                                    searchQuery = vaultSearchQuery,
                                    onSearchQueryChange = viewModel::updateVaultSearchQuery,
                                    onToggleLinkStar = viewModel::toggleLinkStar,
                                    onLinkCopied = viewModel::notifyLinkCopied
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PulseTabItem(
    selected: Boolean,
    label: String,
    badgeCount: Int,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedNavIcon(
                icon = if (selected) selectedIcon else unselectedIcon,
                count = badgeCount,
                contentDescription = label
            )
        },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CyanPulse,
            selectedTextColor = CyanPulse,
            indicatorColor = CyanPulseContainer
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun BadgedNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    contentDescription: String
) {
    if (count > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = CyanPulse, contentColor = ObsidianBackground) {
                    Text(if (count > 99) "99+" else count.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

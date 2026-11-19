package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.launcher.LauncherAppInfo
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NephoApp(
    viewModel: PulseViewModel,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestWeatherLocation: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onDoubleTapScreenOff: () -> Unit = {},
    onEnableScreenLockAdmin: () -> Unit = {}
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
    val allNotifications by viewModel.allActiveNotifications.collectAsStateWithLifecycle()
    val links by viewModel.activeLinks.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val digestSettings by viewModel.digestSettings.collectAsStateWithLifecycle()
    val digestSummaries by viewModel.digestSummaries.collectAsStateWithLifecycle()
    val openDigest by viewModel.openDigest.collectAsStateWithLifecycle()
    val listenerHealth by viewModel.listenerHealth.collectAsStateWithLifecycle()
    val captureDiagnostics by viewModel.captureDiagnostics.collectAsStateWithLifecycle()
    val pendingWidgetThreadId by viewModel.pendingWidgetThreadId.collectAsStateWithLifecycle()
    val updateGithubRepo by viewModel.updateGithubRepo.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()

    // Home Replacement / Launcher states
    val mediaPlaybackState by viewModel.mediaPlaybackState.collectAsStateWithLifecycle()
    val nextAlarmSummary by viewModel.nextAlarmSummary.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    val groupedApps by viewModel.groupedInstalledApps.collectAsStateWithLifecycle()
    val pinnedShortcutApps by viewModel.pinnedShortcutApps.collectAsStateWithLifecycle()
    val pinnedShortcutPackages by viewModel.pinnedShortcutPackages.collectAsStateWithLifecycle()
    val isDefaultHome by viewModel.isDefaultHome.collectAsStateWithLifecycle()
    val isScreenLockEnabled by viewModel.isScreenLockEnabled.collectAsStateWithLifecycle()
    val weatherUiState by viewModel.weatherUiState.collectAsStateWithLifecycle()
    val weatherCityResults by viewModel.weatherCityResults.collectAsStateWithLifecycle()
    val isSearchingWeatherCities by viewModel.isSearchingWeatherCities.collectAsStateWithLifecycle()
    val calendarScheduleState by viewModel.calendarScheduleState.collectAsStateWithLifecycle()
    val preferredMusicApp by viewModel.preferredMusicApp.collectAsStateWithLifecycle()

    val pinnedPackageSet = remember(pinnedShortcutPackages) { pinnedShortcutPackages.toSet() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val portraitListState = rememberLazyListState()
    val landscapeLeftListState = rememberLazyListState()
    val landscapeRightListState = rememberLazyListState()
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    var menuExpanded by remember { mutableStateOf(false) }
    var showComposeDialog by rememberSaveable { mutableStateOf(false) }
    var showShadePrefsDialog by rememberSaveable { mutableStateOf(false) }
    var showDigestDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdatesDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var showWeatherDialog by rememberSaveable { mutableStateOf(false) }
    var showMusicServiceDialog by rememberSaveable { mutableStateOf(false) }
    var showShortcutDrawer by rememberSaveable { mutableStateOf(false) }
    var reminderTargetItem by remember { mutableStateOf<com.example.data.PulseNotificationItem?>(null) }
    var threadTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(openDigest) {
        if (openDigest) {
            showDigestDialog = true
            viewModel.clearOpenDigest()
        }
    }

    LaunchedEffect(pendingWidgetThreadId, allNotifications) {
        val targetId = pendingWidgetThreadId ?: return@LaunchedEffect
        val matching = allNotifications.filter { it.threadId == targetId }
        if (matching.isNotEmpty()) {
            threadTargetId = targetId
            viewModel.markThreadAsRead(matching.filter { !it.isRead }.map { it.key })
            viewModel.clearPendingWidgetThreadId()
        }
    }

    LaunchedEffect(pendingWidgetThreadId) {
        if (pendingWidgetThreadId != null) {
            delay(2_500L)
            viewModel.clearPendingWidgetThreadId()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.homePressedEvents.collect {
            showShortcutDrawer = false
            showQuickAlarmDialog = false
            showWeatherDialog = false
            showMusicServiceDialog = false
            showComposeDialog = false
            showShadePrefsDialog = false
            showDigestDialog = false
            showDiagnosticsDialog = false
            showUpdatesDialog = false
            menuExpanded = false
            reminderTargetItem = null
            threadTargetId = null
            launch { portraitListState.animateScrollToItem(0) }
            launch { landscapeLeftListState.animateScrollToItem(0) }
            launch { landscapeRightListState.animateScrollToItem(0) }
        }
    }

    // Back gesture behavior for Home replacement & overlays
    val shouldInterceptBack = showShortcutDrawer ||
        showQuickAlarmDialog ||
        showWeatherDialog ||
        showMusicServiceDialog ||
        appSearchQuery.isNotEmpty() ||
        isDefaultHome

    BackHandler(enabled = shouldInterceptBack) {
        when {
            showShortcutDrawer -> showShortcutDrawer = false
            showQuickAlarmDialog -> showQuickAlarmDialog = false
            showWeatherDialog -> showWeatherDialog = false
            showMusicServiceDialog -> showMusicServiceDialog = false
            appSearchQuery.isNotEmpty() -> viewModel.updateAppSearchQuery("")
            else -> {
                coroutineScope.launch {
                    portraitListState.animateScrollToItem(0)
                    landscapeLeftListState.animateScrollToItem(0)
                    landscapeRightListState.animateScrollToItem(0)
                }
            }
        }
    }

    if (showQuickAlarmDialog) {
        QuickAlarmDialog(
            onSetAlarm = viewModel::setQuickAlarm,
            onOpenClockApp = viewModel::openClockApp,
            onDismiss = { showQuickAlarmDialog = false }
        )
    }

    if (showWeatherDialog) {
        WeatherSetupDialog(
            weatherState = weatherUiState,
            installedApps = installedApps,
            citySearchResults = weatherCityResults,
            isSearchingCities = isSearchingWeatherCities,
            onSearchCities = viewModel::searchWeatherCities,
            onSelectLocation = viewModel::selectWeatherLocation,
            onUseCurrentLocation = onRequestWeatherLocation,
            onRefreshWeather = viewModel::refreshWeather,
            onToggleUnit = viewModel::toggleWeatherTemperatureUnit,
            onOpenShortcutApp = viewModel::openWeatherAppShortcut,
            onSelectShortcutApp = viewModel::setWeatherAppShortcut,
            onDismiss = { showWeatherDialog = false }
        )
    }

    if (showMusicServiceDialog) {
        MusicServicePickerDialog(
            selectedApp = preferredMusicApp,
            installedApps = installedApps,
            hasSessionAccess = mediaPlaybackState.hasSessionAccess,
            onSelectService = viewModel::setPreferredMusicService,
            onOpenSelectedService = viewModel::openPreferredMusicService,
            onGrantNotificationAccess = onOpenNotificationAccessSettings,
            onDismiss = { showMusicServiceDialog = false }
        )
    }

    if (showDigestDialog) {
        DigestDialog(
            settings = digestSettings,
            summaries = digestSummaries,
            onSetEnabled = viewModel::setDigestEnabled,
            onDismiss = { showDigestDialog = false }
        )
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
            onOpenSystemNotificationSettings = viewModel::openSystemNotificationSettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onDismiss = { showShadePrefsDialog = false }
        )
    }

    if (showDiagnosticsDialog) {
        CaptureDiagnosticsDialog(
            health = listenerHealth,
            diagnostics = captureDiagnostics,
            onRequestRebind = viewModel::requestListenerRebind,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenBatterySettings = viewModel::openBatteryOptimizationSettings,
            onClearLog = viewModel::clearCaptureDiagnostics,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }

    if (showUpdatesDialog) {
        val urlCopiedMsg = stringResource(R.string.updates_url_copied)
        AppUpdatesDialog(
            savedRepoSlug = updateGithubRepo,
            updateState = updateCheckState,
            onCheckUpdates = viewModel::checkForAppUpdates,
            onOpenUrl = viewModel::openExternalUrl,
            onCopyReleasesUrl = { url ->
                viewModel.copyToClipboard("Nepho Releases", url, urlCopiedMsg)
            },
            onDismiss = { showUpdatesDialog = false }
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

    val threadItems = remember(threadTargetId, allNotifications) {
        threadTargetId?.let { id ->
            allNotifications.filter { it.threadId == id }.sortedBy { it.postTime }
        }.orEmpty()
    }
    LaunchedEffect(threadTargetId, threadItems.isEmpty()) {
        if (threadTargetId != null && threadItems.isEmpty()) {
            threadTargetId = null
        }
    }
    if (threadItems.isNotEmpty()) {
        val latestThreadItem = threadItems.last()
        val gifReplyTarget = threadItems.lastOrNull { it.canReply } ?: latestThreadItem
        ThreadPopupDialog(
            threadItems = threadItems,
            onDismissRequest = { threadTargetId = null },
            onOpenApp = viewModel::openSourceApp,
            onToggleStar = viewModel::toggleNotificationStar,
            onOpenReminderDialog = { item -> reminderTargetItem = item },
            shouldDirectShareGif = viewModel.shouldDirectShareGif(
                key = gifReplyTarget.key,
                packageName = latestThreadItem.packageName
            ),
            onStageGif = viewModel::stageGifAttachment,
            onGifLoadFailed = viewModel::notifyGifLoadFailed,
            onSendReply = viewModel::sendInlineReply,
            onDismissThread = { keys -> keys.forEach { viewModel.dismissNotification(it) } },
            onSecondaryAction = viewModel::executeSecondaryAction
        )
    }

    // Calculate item indices dynamically so smooth section & A-Z letter jumping is exact
    val inboxItemCount = computeInboxItemCount(
        selectedTab = selectedTab,
        notifications = notifications,
        links = links,
        selectedAppFilter = selectedAppFilter,
        isNotificationAccessGranted = isNotificationAccessGranted,
        bannerDismissed = bannerDismissed,
        collapsedGroups = collapsedGroups
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscapeSplit = maxWidth >= 640.dp

        val jumpToSection: (HomeSectionTarget) -> Unit = { target ->
            coroutineScope.launch {
                if (isLandscapeSplit) {
                    when (target) {
                        HomeSectionTarget.CLOCK -> landscapeLeftListState.animateScrollToItem(0)
                        HomeSectionTarget.MUSIC -> landscapeLeftListState.animateScrollToItem(1)
                        HomeSectionTarget.CALENDAR -> landscapeLeftListState.animateScrollToItem(2)
                        HomeSectionTarget.APPS -> landscapeLeftListState.animateScrollToItem(3)
                        HomeSectionTarget.FEED -> {
                            viewModel.selectTab(PulseTab.FEED)
                            landscapeRightListState.animateScrollToItem(0)
                        }
                        HomeSectionTarget.VAULT -> {
                            viewModel.selectTab(PulseTab.LINKS_VAULT)
                            landscapeRightListState.animateScrollToItem(0)
                        }
                    }
                } else {
                    when (target) {
                        HomeSectionTarget.CLOCK -> portraitListState.animateScrollToItem(0)
                        HomeSectionTarget.MUSIC -> portraitListState.animateScrollToItem(1)
                        HomeSectionTarget.CALENDAR -> portraitListState.animateScrollToItem(2)
                        HomeSectionTarget.FEED -> {
                            viewModel.selectTab(PulseTab.FEED)
                            portraitListState.animateScrollToItem(3)
                        }
                        HomeSectionTarget.VAULT -> {
                            viewModel.selectTab(PulseTab.LINKS_VAULT)
                            portraitListState.animateScrollToItem(3)
                        }
                        HomeSectionTarget.APPS -> {
                            // 0=Clock, 1=Music, 2=Calendar, 3=InboxSwitcher, 4..4+inboxItemCount-1=InboxItems
                            val appDrawerHeaderIndex = 4 + inboxItemCount
                            portraitListState.animateScrollToItem(appDrawerHeaderIndex)
                        }
                    }
                }
            }
        }

        val jumpToDrawerLetter: (String) -> Unit = { letter ->
            val drawerHeaderIndex = if (isLandscapeSplit) 3 else (4 + inboxItemCount)
            val offsetInsideDrawer = computeLetterOffsetInDrawer(groupedApps, letter)
            if (offsetInsideDrawer >= 0) {
                val targetIndex = drawerHeaderIndex + 1 + offsetInsideDrawer
                coroutineScope.launch {
                    val state: LazyListState = if (isLandscapeSplit) landscapeLeftListState else portraitListState
                    state.animateScrollToItem(targetIndex)
                }
            }
        }

        if (showShortcutDrawer) {
            ShortcutDrawerSheet(
                pinnedApps = pinnedShortcutApps,
                allInstalledApps = installedApps,
                isDefaultHome = isDefaultHome,
                unreadCount = metrics.unreadTotal,
                linksCount = metrics.totalLinks,
                onLaunchApp = viewModel::launchInstalledApp,
                onTogglePin = viewModel::togglePinnedShortcut,
                onMovePinnedShortcut = viewModel::movePinnedShortcut,
                onJumpToSection = jumpToSection,
                onOpenDefaultHomeSettings = viewModel::openDefaultHomeSettings,
                onDismiss = { showShortcutDrawer = false }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MidnightSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = CyanPulse
                    )
                }
            },
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.onHomeButtonPressed() }
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.nepho_mark),
                                        contentDescription = stringResource(R.string.app_name),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .testTag("top_bar_nepho_logo")
                                    )
                                    val appName = stringResource(R.string.app_name)
                                    val tagline = stringResource(R.string.app_header_tagline)
                                    val titleColor = MaterialTheme.colorScheme.onBackground
                                    val taglineColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    val taglineStyle = MaterialTheme.typography.bodySmall
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = titleColor)) {
                                                append(appName)
                                            }
                                            withStyle(
                                                SpanStyle(
                                                    color = taglineColor,
                                                    fontFamily = taglineStyle.fontFamily,
                                                    fontWeight = taglineStyle.fontWeight,
                                                    fontSize = taglineStyle.fontSize
                                                )
                                            ) {
                                                append(tagline)
                                            }
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .testTag("top_bar_title")
                                    )
                                    Text(
                                        text = stringResource(R.string.app_version_short, BuildConfig.VERSION_NAME),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPulse,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag("top_bar_version")
                                    )
                                    val healthDotColor = when {
                                        !listenerHealth.isAccessGranted -> CrimsonError
                                        listenerHealth.isListenerConnected -> EmeraldOk
                                        else -> AmberSignal
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(healthDotColor)
                                    )
                                }
                                val sinceText = timeFmt.format(Date(sessionOpenedAt))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MidnightSurface,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.refreshAccessAndPermissions()
                                            showDiagnosticsDialog = true
                                        }
                                        .testTag("session_status_pill")
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
                                        .testTag("overflow_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more_options)
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isDefaultHome) {
                                                    stringResource(R.string.default_home_active)
                                                } else {
                                                    stringResource(R.string.action_set_default_home)
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isDefaultHome) Icons.Filled.Home else Icons.Outlined.Home,
                                                contentDescription = null,
                                                tint = if (isDefaultHome) EmeraldOk else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.openDefaultHomeSettings()
                                        },
                                        modifier = Modifier.testTag("menu_default_home")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isScreenLockEnabled) {
                                                    stringResource(R.string.action_double_tap_lock_active)
                                                } else {
                                                    stringResource(R.string.action_enable_double_tap_lock)
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isScreenLockEnabled) Icons.Filled.Lock else Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = if (isScreenLockEnabled) EmeraldOk else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            if (isScreenLockEnabled) {
                                                onDoubleTapScreenOff()
                                            } else {
                                                onEnableScreenLockAdmin()
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_double_tap_lock")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.digest_title)) },
                                        onClick = {
                                            menuExpanded = false
                                            showDigestDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_digest")
                                    )
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
                                        text = { Text(stringResource(R.string.action_capture_diagnostics)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.HealthAndSafety, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.refreshAccessAndPermissions()
                                            showDiagnosticsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_capture_diagnostics")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_app_updates)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showUpdatesDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_app_updates")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_add_home_widget)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Widgets, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.requestPinHomeWidget()
                                        },
                                        modifier = Modifier.testTag("menu_add_home_widget")
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
                BottomShortcutDock(
                    pinnedApps = pinnedShortcutApps,
                    onLaunchApp = viewModel::launchInstalledApp,
                    onExpandDrawer = { showShortcutDrawer = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .homeScreenGestures(
                        onDoubleTapScreenOff = onDoubleTapScreenOff,
                        onSwipeLeftGoogleSearch = viewModel::openGoogleSearch
                    )
                    .testTag("home_gesture_surface")
            ) {
                if (isLandscapeSplit) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .testTag("home_landscape_split"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    // Left Pane (~45%): Clock, Music Controls, Searchable A-Z App Drawer
                    LazyColumn(
                        state = landscapeLeftListState,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .testTag("home_landscape_left_list"),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "home_clock") {
                            HomeClockCard(
                                nextAlarmSummary = nextAlarmSummary,
                                onClick = { showQuickAlarmDialog = true },
                                weatherState = weatherUiState,
                                onWeatherClick = { showWeatherDialog = true }
                            )
                        }
                        item(key = "home_music") {
                            HomeMusicCard(
                                mediaState = mediaPlaybackState,
                                onTogglePlayPause = viewModel::mediaTogglePlayPause,
                                onSkipPrevious = viewModel::mediaSkipPrevious,
                                onSkipNext = viewModel::mediaSkipNext,
                                onOpenMusicApp = viewModel::openMusicApp,
                                onGrantNotificationAccess = onOpenNotificationAccessSettings,
                                preferredMusicApp = preferredMusicApp,
                                onChooseMusicService = { showMusicServiceDialog = true }
                            )
                        }
                        item(key = "home_calendar") {
                            HomeCalendarCard(
                                scheduleState = calendarScheduleState,
                                onRequestCalendarPermission = onRequestCalendarPermission,
                                onRefresh = viewModel::refreshCalendarSchedule,
                                onOpenEvent = viewModel::openCalendarEvent,
                                onOpenCalendarApp = viewModel::openGoogleCalendar
                            )
                        }
                        appDrawerSectionItems(
                            totalInstalledCount = installedApps.size,
                            searchQuery = appSearchQuery,
                            groupedApps = groupedApps,
                            pinnedPackages = pinnedPackageSet,
                            onSearchQueryChange = viewModel::updateAppSearchQuery,
                            onJumpToLetter = jumpToDrawerLetter,
                            onLaunchApp = viewModel::launchInstalledApp,
                            onTogglePin = viewModel::togglePinnedShortcut
                        )
                        item(key = "landscape_left_bottom_spacer") {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Right Pane (~55%): Feed / Links Vault switcher + items
                    LazyColumn(
                        state = landscapeRightListState,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .testTag("home_landscape_right_list"),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "home_inbox_header") {
                            InboxModeSwitcher(
                                selectedTab = selectedTab,
                                unreadCount = metrics.unreadTotal,
                                linksCount = metrics.totalLinks,
                                onSelectTab = viewModel::selectTab
                            )
                        }
                        when (selectedTab) {
                            PulseTab.FEED -> {
                                feedSectionItems(
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
                                    timeFormatter = timeFmt,
                                    collapsedGroups = collapsedGroups,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onSelectAppFilter = viewModel::selectAppFilter,
                                    onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                                    onToggleHasLinksOnly = viewModel::toggleHasLinksOnly,
                                    onToggleStarredOnly = viewModel::toggleStarredOnly,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                                    onOpenShadePreferences = { showShadePrefsDialog = true },
                                    onDismissBanner = viewModel::dismissBanner,
                                    onOpenThread = { item ->
                                        threadTargetId = item.threadId
                                        viewModel.markThreadAsRead(
                                            allNotifications
                                                .filter { it.threadId == item.threadId && !it.isRead }
                                                .map { it.key }
                                        )
                                    },
                                    onOpenApp = viewModel::openSourceApp,
                                    onOpenSelectedApp = viewModel::openSelectedApp,
                                    onToggleStar = viewModel::toggleNotificationStar,
                                    onSwipeToggleStar = viewModel::toggleNotificationStarFromSwipe,
                                    onSwipeDismissItem = viewModel::dismissNotificationWithUndo,
                                    onSwipeStarBlocked = viewModel::notifyStarredSwipeProtected,
                                    onMarkGroupRead = viewModel::markAppGroupAsRead,
                                    onClearGroup = viewModel::clearAppGroupWithUndo,
                                    onOpenReminderDialog = { item -> reminderTargetItem = item },
                                    onDismissItem = viewModel::dismissNotificationWithUndo,
                                    onOpenVaultTab = { viewModel.selectTab(PulseTab.LINKS_VAULT) }
                                )
                            }
                            PulseTab.LINKS_VAULT -> {
                                linksVaultSectionItems(
                                    links = links,
                                    searchQuery = vaultSearchQuery,
                                    timeFormatter = timeFmt,
                                    context = context,
                                    onSearchQueryChange = viewModel::updateVaultSearchQuery,
                                    onToggleLinkStar = viewModel::toggleLinkStar
                                )
                            }
                        }
                        item(key = "landscape_right_bottom_spacer") {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            } else {
                // Portrait: Single continuous vertical scroll (longer than one page)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = portraitListState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .testTag("home_vertical_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "home_clock") {
                            HomeClockCard(
                                nextAlarmSummary = nextAlarmSummary,
                                onClick = { showQuickAlarmDialog = true },
                                weatherState = weatherUiState,
                                onWeatherClick = { showWeatherDialog = true }
                            )
                        }

                        item(key = "home_music") {
                            HomeMusicCard(
                                mediaState = mediaPlaybackState,
                                onTogglePlayPause = viewModel::mediaTogglePlayPause,
                                onSkipPrevious = viewModel::mediaSkipPrevious,
                                onSkipNext = viewModel::mediaSkipNext,
                                onOpenMusicApp = viewModel::openMusicApp,
                                onGrantNotificationAccess = onOpenNotificationAccessSettings,
                                preferredMusicApp = preferredMusicApp,
                                onChooseMusicService = { showMusicServiceDialog = true }
                            )
                        }

                        item(key = "home_calendar") {
                            HomeCalendarCard(
                                scheduleState = calendarScheduleState,
                                onRequestCalendarPermission = onRequestCalendarPermission,
                                onRefresh = viewModel::refreshCalendarSchedule,
                                onOpenEvent = viewModel::openCalendarEvent,
                                onOpenCalendarApp = viewModel::openGoogleCalendar
                            )
                        }

                        item(key = "home_inbox_header") {
                            InboxModeSwitcher(
                                selectedTab = selectedTab,
                                unreadCount = metrics.unreadTotal,
                                linksCount = metrics.totalLinks,
                                onSelectTab = viewModel::selectTab
                            )
                        }

                        when (selectedTab) {
                            PulseTab.FEED -> {
                                feedSectionItems(
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
                                    timeFormatter = timeFmt,
                                    collapsedGroups = collapsedGroups,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onSelectAppFilter = viewModel::selectAppFilter,
                                    onToggleUnreadOnly = viewModel::toggleUnreadOnly,
                                    onToggleHasLinksOnly = viewModel::toggleHasLinksOnly,
                                    onToggleStarredOnly = viewModel::toggleStarredOnly,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                                    onOpenShadePreferences = { showShadePrefsDialog = true },
                                    onDismissBanner = viewModel::dismissBanner,
                                    onOpenThread = { item ->
                                        threadTargetId = item.threadId
                                        viewModel.markThreadAsRead(
                                            allNotifications
                                                .filter { it.threadId == item.threadId && !it.isRead }
                                                .map { it.key }
                                        )
                                    },
                                    onOpenApp = viewModel::openSourceApp,
                                    onOpenSelectedApp = viewModel::openSelectedApp,
                                    onToggleStar = viewModel::toggleNotificationStar,
                                    onSwipeToggleStar = viewModel::toggleNotificationStarFromSwipe,
                                    onSwipeDismissItem = viewModel::dismissNotificationWithUndo,
                                    onSwipeStarBlocked = viewModel::notifyStarredSwipeProtected,
                                    onMarkGroupRead = viewModel::markAppGroupAsRead,
                                    onClearGroup = viewModel::clearAppGroupWithUndo,
                                    onOpenReminderDialog = { item -> reminderTargetItem = item },
                                    onDismissItem = viewModel::dismissNotificationWithUndo,
                                    onOpenVaultTab = { viewModel.selectTab(PulseTab.LINKS_VAULT) }
                                )
                            }
                            PulseTab.LINKS_VAULT -> {
                                linksVaultSectionItems(
                                    links = links,
                                    searchQuery = vaultSearchQuery,
                                    timeFormatter = timeFmt,
                                    context = context,
                                    onSearchQueryChange = viewModel::updateVaultSearchQuery,
                                    onToggleLinkStar = viewModel::toggleLinkStar
                                )
                            }
                        }

                        appDrawerSectionItems(
                            totalInstalledCount = installedApps.size,
                            searchQuery = appSearchQuery,
                            groupedApps = groupedApps,
                            pinnedPackages = pinnedPackageSet,
                            onSearchQueryChange = viewModel::updateAppSearchQuery,
                            onJumpToLetter = jumpToDrawerLetter,
                            onLaunchApp = viewModel::launchInstalledApp,
                            onTogglePin = viewModel::togglePinnedShortcut
                        )

                        item(key = "portrait_bottom_spacer") {
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun InboxModeSwitcher(
    selectedTab: PulseTab,
    unreadCount: Int,
    linksCount: Int,
    onSelectTab: (PulseTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, BorderHairline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_inbox_switcher")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InboxSegmentButton(
                selected = selectedTab == PulseTab.FEED,
                label = stringResource(R.string.tab_feed),
                badgeCount = unreadCount,
                icon = if (selectedTab == PulseTab.FEED) Icons.Default.DynamicFeed else Icons.Outlined.DynamicFeed,
                testTag = "nav_tab_feed",
                onClick = { onSelectTab(PulseTab.FEED) },
                modifier = Modifier.weight(1f)
            )
            InboxSegmentButton(
                selected = selectedTab == PulseTab.LINKS_VAULT,
                label = stringResource(R.string.tab_vault),
                badgeCount = linksCount,
                icon = if (selectedTab == PulseTab.LINKS_VAULT) Icons.Default.Link else Icons.Outlined.Link,
                testTag = "nav_tab_vault",
                onClick = { onSelectTab(PulseTab.LINKS_VAULT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InboxSegmentButton(
    selected: Boolean,
    label: String,
    badgeCount: Int,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CyanPulseContainer else ElevatedSurface.copy(alpha = 0.45f),
        border = if (selected) BorderStroke(1.dp, CyanPulse.copy(alpha = 0.45f)) else null,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeCount > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (selected) CyanPulse else BorderHairline
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) ObsidianBackground else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun computeInboxItemCount(
    selectedTab: PulseTab,
    notifications: List<com.example.data.PulseNotificationItem>,
    links: List<com.example.data.ExtractedLinkItem>,
    selectedAppFilter: com.example.data.SupportedApp?,
    isNotificationAccessGranted: Boolean,
    bannerDismissed: Boolean,
    collapsedGroups: Map<String, Boolean>
): Int {
    return when (selectedTab) {
        PulseTab.FEED -> {
            var count = 0
            if (!isNotificationAccessGranted || !bannerDismissed) count += 1 // onboarding banner
            count += 1 // search & filters
            if (selectedAppFilter != null) count += 1 // selected_app_link
            if (notifications.isNotEmpty()) {
                val groups = notifications.groupBy { it.packageName }
                groups.forEach { (pkg, items) ->
                    count += 1 // group header
                    if (collapsedGroups[pkg] != true) {
                        count += items.size
                    }
                }
            }
            count
        }
        PulseTab.LINKS_VAULT -> {
            1 + if (links.isEmpty()) 1 else links.size
        }
    }
}

private fun computeLetterOffsetInDrawer(
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

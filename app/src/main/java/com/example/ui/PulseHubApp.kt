package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.local.HomeSectionId
import com.example.data.local.HomeSectionPrefs
import com.example.launcher.LauncherAppInfo
import com.example.ui.theme.LocalCalmVisualPrefs
import com.example.ui.theme.LocalNephoPalette
import com.example.ui.theme.MonochromeIconColorFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NephoApp(
    viewModel: PulseViewModel,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestWeatherLocation: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onDoubleTapScreenOff: () -> Unit = {},
    onEnableScreenLockAdmin: () -> Unit = {},
    onHideKeyboard: () -> Unit = {},
    onRestartNepho: () -> Unit = {}
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val bannerDismissed by viewModel.bannerDismissed.collectAsStateWithLifecycle()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val defaultShadeMode by viewModel.defaultShadeMode.collectAsStateWithLifecycle()
    val shadePreferenceItems by viewModel.shadePreferenceItems.collectAsStateWithLifecycle()
    val vaultSearchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
    val selectedAppFilter by viewModel.selectedAppFilter.collectAsStateWithLifecycle()
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val hasLinksOnly by viewModel.hasLinksOnly.collectAsStateWithLifecycle()
    val starredOnly by viewModel.starredOnly.collectAsStateWithLifecycle()
    val notifications by viewModel.filteredNotifications.collectAsStateWithLifecycle()
    val allNotifications by viewModel.allActiveNotifications.collectAsStateWithLifecycle()
    val links by viewModel.activeLinks.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
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
    val homeSections by viewModel.homeSections.collectAsStateWithLifecycle()
    val calmVisualPrefs by viewModel.calmVisualPrefs.collectAsStateWithLifecycle()

    val palette = LocalNephoPalette.current
    val pinnedPackageSet = remember(pinnedShortcutPackages) { pinnedShortcutPackages.toSet() }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardInputBoundsRegistry = remember { KeyboardInputBoundsRegistry() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val portraitListState = rememberLazyListState()
    val landscapeLeftListState = rememberLazyListState()
    val landscapeRightListState = rememberLazyListState()
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    var menuExpanded by remember { mutableStateOf(false) }
    var showComposeDialog by rememberSaveable { mutableStateOf(false) }
    var showShadePrefsDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdatesDialog by rememberSaveable { mutableStateOf(false) }
    var showWeatherDialog by rememberSaveable { mutableStateOf(false) }
    var showMusicServiceDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeSectionsDialog by rememberSaveable { mutableStateOf(false) }
    var showShortcutDrawer by rememberSaveable { mutableStateOf(false) }
    var showAlphabeticalAppDrawer by rememberSaveable { mutableStateOf(false) }
    var feedFiltersExpanded by rememberSaveable { mutableStateOf(false) }
    var isAppSearchFocused by remember { mutableStateOf(false) }
    var isVaultSearchFocused by remember { mutableStateOf(false) }
    var reminderTargetItem by remember { mutableStateOf<com.example.data.PulseNotificationItem?>(null) }
    var threadTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isImeVisible = WindowInsets.isImeVisible
    val isAppSearchElevated = isAppSearchFocused || appSearchQuery.isNotBlank()

    val dismissKeyboard: () -> Unit = {
        isAppSearchFocused = false
        isVaultSearchFocused = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onHideKeyboard()
    }

    val openAlphabeticalAppDrawer: () -> Unit = {
        dismissKeyboard()
        showShortcutDrawer = false
        showAlphabeticalAppDrawer = true
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
            dismissKeyboard()
            showShortcutDrawer = false
            showAlphabeticalAppDrawer = false
            showWeatherDialog = false
            showMusicServiceDialog = false
            showComposeDialog = false
            showShadePrefsDialog = false
            showDiagnosticsDialog = false
            showUpdatesDialog = false
            showHomeSectionsDialog = false
            menuExpanded = false
            reminderTargetItem = null
            threadTargetId = null
            launch { portraitListState.animateScrollToItem(0) }
            launch { landscapeLeftListState.animateScrollToItem(0) }
            launch { landscapeRightListState.animateScrollToItem(0) }
        }
    }

    LaunchedEffect(showShortcutDrawer, showAlphabeticalAppDrawer) {
        if (showShortcutDrawer || showAlphabeticalAppDrawer) {
            dismissKeyboard()
        }
    }

    // Back gesture behavior for Home replacement & overlays
    val shouldInterceptBack = showAlphabeticalAppDrawer ||
        showShortcutDrawer ||
        showWeatherDialog ||
        showMusicServiceDialog ||
        appSearchQuery.isNotEmpty() ||
        isDefaultHome

    BackHandler(enabled = shouldInterceptBack) {
        dismissKeyboard()
        when {
            showAlphabeticalAppDrawer -> showAlphabeticalAppDrawer = false
            showShortcutDrawer -> showShortcutDrawer = false
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

    if (showHomeSectionsDialog) {
        HomeSectionsDialog(
            sections = homeSections,
            onSetSections = viewModel::setHomeSections,
            onDismiss = { showHomeSectionsDialog = false },
            calmPrefs = calmVisualPrefs,
            onSetCalmPrefs = viewModel::setCalmVisualPrefs,
            onResetLayout = viewModel::resetHomeLayout
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
            onDismiss = { showDiagnosticsDialog = false },
            onRestartNepho = {
                showDiagnosticsDialog = false
                onRestartNepho()
            }
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

    // Calculate item counts & indices dynamically so section reordering, minimizing, and A-Z jumping stay exact
    val inboxItemCount = computeInboxItemCount(
        selectedTab = selectedTab,
        notifications = notifications,
        links = links,
        selectedAppFilter = selectedAppFilter,
        unreadOnly = unreadOnly,
        hasLinksOnly = hasLinksOnly,
        starredOnly = starredOnly,
        isNotificationAccessGranted = isNotificationAccessGranted,
        bannerDismissed = bannerDismissed,
        collapsedGroups = collapsedGroups
    )
    val drawerItemCount = computeAppDrawerItemCount(groupedApps)
    val orderedPortraitSections = homeSections.normalizedOrder
    val orderedLandscapeLeftSections = remember(orderedPortraitSections) {
        orderedPortraitSections.filter { it != HomeSectionId.INBOX }
    }
    val portraitIndices = remember(homeSections, inboxItemCount, drawerItemCount) {
        computeSectionStartIndices(
            sections = homeSections,
            orderedSectionIds = homeSections.normalizedOrder,
            inboxItemCount = inboxItemCount,
            drawerItemCount = drawerItemCount
        )
    }
    val landscapeLeftIndices = remember(homeSections, orderedLandscapeLeftSections, drawerItemCount) {
        computeSectionStartIndices(
            sections = homeSections,
            orderedSectionIds = orderedLandscapeLeftSections,
            inboxItemCount = 0,
            drawerItemCount = drawerItemCount
        )
    }

    val clearAllFeedFilters: () -> Unit = {
        if (selectedAppFilter != null) viewModel.selectAppFilter(null)
        if (unreadOnly) viewModel.toggleUnreadOnly()
        if (hasLinksOnly) viewModel.toggleHasLinksOnly()
        if (starredOnly) viewModel.toggleStarredOnly()
    }

    CompositionLocalProvider(
        LocalKeyboardInputBoundsRegistry provides keyboardInputBoundsRegistry
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .dismissKeyboardOnOutsideInteraction(
                    registry = keyboardInputBoundsRegistry,
                    shouldDismiss = { isImeVisible || isAppSearchFocused || isVaultSearchFocused },
                    onDismissKeyboard = dismissKeyboard
                )
        ) {
        val isLandscapeSplit = maxWidth >= 640.dp
        val drawerHeaderIndex = (if (isLandscapeSplit) {
            landscapeLeftIndices[HomeSectionId.APPS]
        } else {
            portraitIndices[HomeSectionId.APPS]
        }) ?: 0
        val searchRunwayHeight = (maxHeight * 0.65f).coerceAtLeast(280.dp)

        val scrollAppSearchToTop: () -> Unit = {
            coroutineScope.launch {
                val state: LazyListState = if (isLandscapeSplit) landscapeLeftListState else portraitListState
                state.animateScrollToItem(drawerHeaderIndex)
                delay(180L)
                state.animateScrollToItem(drawerHeaderIndex)
            }
        }

        LaunchedEffect(isAppSearchFocused, isImeVisible, drawerHeaderIndex) {
            if (isAppSearchFocused && isImeVisible && homeSections.showAppDrawer && !homeSections.isMinimized(HomeSectionId.APPS)) {
                val state = if (isLandscapeSplit) landscapeLeftListState else portraitListState
                state.animateScrollToItem(drawerHeaderIndex)
            }
        }

        LaunchedEffect(appSearchQuery, isAppSearchFocused, drawerHeaderIndex) {
            if (isAppSearchFocused && homeSections.showAppDrawer && !homeSections.isMinimized(HomeSectionId.APPS)) {
                val state = if (isLandscapeSplit) landscapeLeftListState else portraitListState
                state.animateScrollToItem(drawerHeaderIndex)
            }
        }

        val jumpToSection: (HomeSectionTarget) -> Unit = { target ->
            if (target == HomeSectionTarget.APPS && !homeSections.showAppDrawer) {
                openAlphabeticalAppDrawer()
            } else {
                val targetSectionId = when (target) {
                    HomeSectionTarget.CLOCK -> HomeSectionId.CLOCK
                    HomeSectionTarget.MUSIC -> HomeSectionId.MUSIC
                    HomeSectionTarget.CALENDAR -> HomeSectionId.CALENDAR
                    HomeSectionTarget.FEED,
                    HomeSectionTarget.VAULT -> HomeSectionId.INBOX
                    HomeSectionTarget.APPS -> HomeSectionId.APPS
                }
                if (homeSections.isMinimized(targetSectionId)) {
                    viewModel.setHomeSectionMinimized(targetSectionId, false)
                }
                if (target == HomeSectionTarget.FEED) {
                    viewModel.selectTab(PulseTab.FEED)
                } else if (target == HomeSectionTarget.VAULT) {
                    viewModel.selectTab(PulseTab.LINKS_VAULT)
                }
                coroutineScope.launch {
                    if (isLandscapeSplit) {
                        if (targetSectionId == HomeSectionId.INBOX) {
                            landscapeRightListState.animateScrollToItem(0)
                        } else {
                            val idx = landscapeLeftIndices[targetSectionId] ?: 0
                            landscapeLeftListState.animateScrollToItem(idx)
                        }
                    } else {
                        val idx = portraitIndices[targetSectionId] ?: 0
                        portraitListState.animateScrollToItem(idx)
                    }
                }
            }
        }

        val jumpToDrawerLetter: (String) -> Unit = { letter ->
            val offsetInsideDrawer = computeLetterOffsetInDrawer(groupedApps, letter)
            if (offsetInsideDrawer >= 0 && homeSections.showAppDrawer && !homeSections.isMinimized(HomeSectionId.APPS)) {
                val targetIndex = drawerHeaderIndex + 1 + offsetInsideDrawer
                coroutineScope.launch {
                    val state: LazyListState = if (isLandscapeSplit) landscapeLeftListState else portraitListState
                    state.animateScrollToItem(targetIndex)
                }
            }
        }

        fun LazyListScope.emitHomeSection(sectionId: HomeSectionId) {
            val isMinimized = homeSections.isMinimized(sectionId)
            when (sectionId) {
                HomeSectionId.CLOCK -> {
                    if (isMinimized) {
                        item(key = "home_clock_minimized") {
                            MinimizedClockBar(
                                weatherState = weatherUiState,
                                onExpand = { viewModel.setHomeSectionMinimized(HomeSectionId.CLOCK, false) }
                            )
                        }
                    } else {
                        item(key = "home_clock") {
                            HomeClockCard(
                                nextAlarmSummary = nextAlarmSummary,
                                onClick = viewModel::openClockApp,
                                weatherState = weatherUiState,
                                onWeatherClick = { showWeatherDialog = true },
                                onMinimize = { viewModel.toggleHomeSectionMinimized(HomeSectionId.CLOCK) }
                            )
                        }
                    }
                }

                HomeSectionId.MUSIC -> {
                    if (isMinimized) {
                        item(key = "home_music_minimized") {
                            MinimizedMusicBar(
                                mediaState = mediaPlaybackState,
                                onTogglePlayPause = viewModel::mediaTogglePlayPause,
                                onExpand = { viewModel.setHomeSectionMinimized(HomeSectionId.MUSIC, false) }
                            )
                        }
                    } else {
                        item(key = "home_music") {
                            HomeMusicCard(
                                mediaState = mediaPlaybackState,
                                onTogglePlayPause = viewModel::mediaTogglePlayPause,
                                onSkipPrevious = viewModel::mediaSkipPrevious,
                                onSkipNext = viewModel::mediaSkipNext,
                                onOpenMusicApp = viewModel::openMusicApp,
                                onGrantNotificationAccess = onOpenNotificationAccessSettings,
                                preferredMusicApp = preferredMusicApp,
                                onChooseMusicService = { showMusicServiceDialog = true },
                                onMinimize = { viewModel.toggleHomeSectionMinimized(HomeSectionId.MUSIC) }
                            )
                        }
                    }
                }

                HomeSectionId.CALENDAR -> {
                    if (isMinimized) {
                        item(key = "home_calendar_minimized") {
                            MinimizedCalendarBar(
                                scheduleState = calendarScheduleState,
                                onExpand = { viewModel.setHomeSectionMinimized(HomeSectionId.CALENDAR, false) }
                            )
                        }
                    } else {
                        item(key = "home_calendar") {
                            HomeCalendarCard(
                                scheduleState = calendarScheduleState,
                                onRequestCalendarPermission = onRequestCalendarPermission,
                                onRefresh = viewModel::refreshCalendarSchedule,
                                onOpenEvent = viewModel::openCalendarEvent,
                                onOpenCalendarApp = viewModel::openGoogleCalendar,
                                onMinimize = { viewModel.toggleHomeSectionMinimized(HomeSectionId.CALENDAR) }
                            )
                        }
                    }
                }

                HomeSectionId.INBOX -> {
                    if (isMinimized) {
                        item(key = "home_inbox_minimized") {
                            MinimizedInboxBar(
                                unreadCount = metrics.unreadTotal,
                                linksCount = metrics.totalLinks,
                                onExpand = { viewModel.setHomeSectionMinimized(HomeSectionId.INBOX, false) },
                                totalInboxCount = allNotifications.size
                            )
                        }
                    } else {
                        item(key = "home_inbox_header") {
                            InboxModeSwitcher(
                                selectedTab = selectedTab,
                                unreadCount = metrics.unreadTotal,
                                linksCount = metrics.totalLinks,
                                onSelectTab = viewModel::selectTab,
                                onMinimize = { viewModel.toggleHomeSectionMinimized(HomeSectionId.INBOX) },
                                totalInboxCount = allNotifications.size
                            )
                        }
                        when (selectedTab) {
                            PulseTab.FEED -> {
                                feedSectionItems(
                                    notifications = notifications,
                                    selectedAppFilter = selectedAppFilter,
                                    unreadOnly = unreadOnly,
                                    hasLinksOnly = hasLinksOnly,
                                    starredOnly = starredOnly,
                                    bannerDismissed = bannerDismissed,
                                    hasNotificationPermission = hasNotificationPermission,
                                    isNotificationAccessGranted = isNotificationAccessGranted,
                                    timeFormatter = timeFmt,
                                    collapsedGroups = collapsedGroups,
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
                                    onOpenVaultTab = { viewModel.selectTab(PulseTab.LINKS_VAULT) },
                                    filtersExpanded = feedFiltersExpanded,
                                    onToggleFiltersExpanded = { feedFiltersExpanded = !feedFiltersExpanded },
                                    onClearAllFilters = clearAllFeedFilters
                                )
                            }
                            PulseTab.LINKS_VAULT -> {
                                linksVaultSectionItems(
                                    links = links,
                                    searchQuery = vaultSearchQuery,
                                    timeFormatter = timeFmt,
                                    context = context,
                                    onSearchQueryChange = viewModel::updateVaultSearchQuery,
                                    onToggleLinkStar = viewModel::toggleLinkStar,
                                    onSearchFocusChanged = { focused ->
                                        isVaultSearchFocused = focused
                                    }
                                )
                            }
                        }
                    }
                }

                HomeSectionId.APPS -> {
                    if (isMinimized) {
                        item(key = "home_apps_minimized") {
                            MinimizedAppDrawerBar(
                                totalInstalledCount = installedApps.size,
                                onExpand = { viewModel.setHomeSectionMinimized(HomeSectionId.APPS, false) },
                                onQuickSearch = {
                                    viewModel.setHomeSectionMinimized(HomeSectionId.APPS, false)
                                    isAppSearchFocused = true
                                    scrollAppSearchToTop()
                                }
                            )
                        }
                    } else {
                        appDrawerSectionItems(
                            totalInstalledCount = installedApps.size,
                            searchQuery = appSearchQuery,
                            groupedApps = groupedApps,
                            pinnedPackages = pinnedPackageSet,
                            onSearchQueryChange = viewModel::updateAppSearchQuery,
                            onJumpToLetter = jumpToDrawerLetter,
                            onLaunchApp = viewModel::launchInstalledApp,
                            onTogglePin = viewModel::togglePinnedShortcut,
                            onSearchFocusChanged = { focused ->
                                isAppSearchFocused = focused
                                if (focused) scrollAppSearchToTop()
                            },
                            onMinimize = { viewModel.toggleHomeSectionMinimized(HomeSectionId.APPS) }
                        )
                    }
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

        if (showAlphabeticalAppDrawer) {
            AlphabeticalAppDrawerSheet(
                installedApps = installedApps,
                pinnedPackages = pinnedPackageSet,
                onLaunchApp = viewModel::launchInstalledApp,
                onTogglePin = viewModel::togglePinnedShortcut,
                onDismiss = { showAlphabeticalAppDrawer = false }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = palette.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = palette.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = palette.accent
                    )
                }
            },
            topBar = {
                Column(modifier = Modifier.background(palette.background)) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            NephoHeaderTitle(
                                versionName = BuildConfig.VERSION_NAME,
                                listenerHealth = listenerHealth,
                                onClick = { viewModel.onHomeButtonPressed() }
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showHomeSectionsDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_layout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Dashboard,
                                    contentDescription = stringResource(R.string.action_home_layout_and_calm),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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

                            val isYellowStatus = listenerHealth.isAccessGranted && !listenerHealth.isListenerConnected

                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("overflow_menu_button")
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.action_more_options)
                                        )
                                        if (isYellowStatus) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(palette.amber)
                                                    .testTag("overflow_menu_yellow_dot")
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (isYellowStatus) {
                                        RestartNephoMenuItem(
                                            isYellowStatus = true,
                                            onClick = {
                                                menuExpanded = false
                                                onRestartNepho()
                                            }
                                        )
                                    }
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
                                                tint = if (isDefaultHome) palette.emerald else MaterialTheme.colorScheme.onSurface
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
                                                tint = if (isScreenLockEnabled) palette.emerald else MaterialTheme.colorScheme.onSurface
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
                                        text = { Text(stringResource(R.string.action_home_layout_and_calm)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Dashboard, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showHomeSectionsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_home_sections")
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
                                    if (!isYellowStatus) {
                                        RestartNephoMenuItem(
                                            isYellowStatus = false,
                                            onClick = {
                                                menuExpanded = false
                                                onRestartNepho()
                                            }
                                        )
                                    }
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
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isImeVisible) {
                    if (homeSections.showShortcutDock) {
                        BottomShortcutDock(
                            pinnedApps = pinnedShortcutApps,
                            onLaunchApp = viewModel::launchInstalledApp,
                            onExpandDrawer = { showShortcutDrawer = true },
                            onSwipeUp = openAlphabeticalAppDrawer
                        )
                    } else {
                        BottomEdgeAppDrawerHandle(
                            onOpenDrawer = openAlphabeticalAppDrawer
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .homeScreenGestures(
                        onDoubleTapScreenOff = onDoubleTapScreenOff,
                        onSwipeLeftGoogleSearch = viewModel::openGoogleSearch,
                        canScrollForward = if (isLandscapeSplit) {
                            landscapeLeftListState.canScrollForward ||
                                landscapeRightListState.canScrollForward
                        } else {
                            portraitListState.canScrollForward
                        },
                        onSwipeUpWhenCannotScroll = openAlphabeticalAppDrawer,
                        onSwipeUpFromBottom = openAlphabeticalAppDrawer,
                        bottomSwipeUpZoneHeight = if (homeSections.showShortcutDock) 64.dp else 96.dp
                    )
                    .testTag("home_gesture_surface")
            ) {
                val allSectionsMinimized = areAllVisibleSectionsMinimized(homeSections)
                val inboxShowingEmptyQuote = homeSections.showInbox &&
                    !homeSections.isMinimized(HomeSectionId.INBOX) &&
                    selectedTab == PulseTab.FEED &&
                    allNotifications.isEmpty() &&
                    notifications.isEmpty() &&
                    selectedAppFilter == null &&
                    !unreadOnly &&
                    !hasLinksOnly &&
                    !starredOnly &&
                    isNotificationAccessGranted
                val showHomeCanvasQuote = allSectionsMinimized ||
                    (allNotifications.isEmpty() && !inboxShowingEmptyQuote)

                if (isLandscapeSplit) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .testTag("home_landscape_split"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val visibleLeftSections = orderedLandscapeLeftSections.filter {
                            homeSections.isVisible(it)
                        }
                        if (visibleLeftSections.isNotEmpty() || !homeSections.showInbox) {
                            LazyColumn(
                                state = landscapeLeftListState,
                                modifier = Modifier
                                    .weight(if (homeSections.showInbox) 0.45f else 1f)
                                    .fillMaxHeight()
                                    .testTag("home_landscape_left_list"),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                visibleLeftSections.forEach { sectionId ->
                                    emitHomeSection(sectionId)
                                }
                                if (showHomeCanvasQuote && !homeSections.showInbox) {
                                    item(key = "home_soothing_quote_left") {
                                        SoothingQuotesCard()
                                    }
                                }
                                item(key = "landscape_left_bottom_spacer") {
                                    Spacer(
                                        Modifier.height(
                                            if (isAppSearchElevated || isImeVisible) searchRunwayHeight else 16.dp
                                        )
                                    )
                                }
                            }
                        }

                        if (homeSections.showInbox) {
                            LazyColumn(
                                state = landscapeRightListState,
                                modifier = Modifier
                                    .weight(if (visibleLeftSections.isNotEmpty()) 0.55f else 1f)
                                    .fillMaxHeight()
                                    .testTag("home_landscape_right_list"),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                emitHomeSection(HomeSectionId.INBOX)
                                if (showHomeCanvasQuote) {
                                    item(key = "home_soothing_quote_right") {
                                        SoothingQuotesCard()
                                    }
                                }
                                item(key = "landscape_right_bottom_spacer") {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Portrait: Single continuous vertical scroll in user's preferred section order
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
                            orderedPortraitSections
                                .filter { homeSections.isVisible(it) }
                                .forEach { sectionId ->
                                    emitHomeSection(sectionId)
                                }

                            if (showHomeCanvasQuote) {
                                item(key = "home_soothing_quote_portrait") {
                                    SoothingQuotesCard()
                                }
                            }

                            item(key = "portrait_bottom_spacer") {
                                Spacer(
                                    Modifier.height(
                                        if (isAppSearchElevated || isImeVisible) searchRunwayHeight else 20.dp
                                    )
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

@Composable
fun NephoHeaderTitle(
    versionName: String,
    listenerHealth: ListenerHealthState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNephoPalette.current
    val calmPrefs = LocalCalmVisualPrefs.current
    val healthDotColor = when {
        !listenerHealth.isAccessGranted -> palette.crimson
        listenerHealth.isListenerConnected -> palette.emerald
        else -> palette.amber
    }
    val isYellowReconnecting = listenerHealth.isAccessGranted &&
        !listenerHealth.isListenerConnected &&
        listenerHealth.isReconnecting
    val healthDotDescription = stringResource(
        when {
            !listenerHealth.isAccessGranted -> R.string.health_dot_access_off_desc
            listenerHealth.isListenerConnected -> R.string.health_dot_connected_desc
            isYellowReconnecting -> R.string.health_dot_reconnecting_desc
            else -> R.string.health_dot_disconnected_desc
        }
    )
    val reconnectingPulse = if (isYellowReconnecting) {
        val transition = rememberInfiniteTransition(label = "health_dot_reconnect_pulse")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "health_dot_pulse_value"
        )
        pulse
    } else {
        1f
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("top_bar_brand_area")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.nepho_mark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                colorFilter = if (calmPrefs.monochromeIcons) MonochromeIconColorFilter else null,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .alpha(if (calmPrefs.monochromeIcons) 0.88f else 1f)
                    .testTag("top_bar_nepho_logo")
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("top_bar_title")
            )
            val versionShape = RoundedCornerShape(6.dp)
            Surface(
                shape = versionShape,
                color = palette.surface,
                modifier = Modifier
                    .clip(versionShape)
                    .calmingLitBorder(
                        accentColor = palette.accent,
                        cornerRadius = 6.dp,
                        strokeWidth = 1.dp,
                        intensity = CalmBorderIntensity.SUBTLE
                    )
            ) {
                Text(
                    text = stringResource(R.string.app_version_short, versionName),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("top_bar_version")
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isYellowReconnecting) 0.86f + (0.30f * reconnectingPulse) else 1f)
                    .alpha(if (isYellowReconnecting) 0.40f + (0.60f * reconnectingPulse) else 1f)
                    .clip(CircleShape)
                    .background(healthDotColor)
                    .semantics { contentDescription = healthDotDescription }
                    .testTag("top_bar_health_dot")
            )
        }
        Text(
            text = stringResource(R.string.app_header_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(start = 34.dp)
                .testTag("top_bar_tagline")
        )
    }
}

@Composable
fun RestartNephoMenuItem(
    isYellowStatus: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalNephoPalette.current
    val highlightColor = if (isYellowStatus) palette.amber else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_restart_nepho),
                        color = highlightColor,
                        fontWeight = if (isYellowStatus) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isYellowStatus) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(palette.amber)
                                .testTag("menu_restart_yellow_indicator")
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (isYellowStatus) {
                            R.string.restart_nepho_yellow_subtitle
                        } else {
                            R.string.restart_nepho_subtitle
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isYellowStatus) {
                        palette.amber.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = highlightColor
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag("menu_restart_nepho")
    )
}

@Composable
fun InboxModeSwitcher(
    selectedTab: PulseTab,
    unreadCount: Int,
    linksCount: Int,
    onSelectTab: (PulseTab) -> Unit,
    onMinimize: (() -> Unit)? = null,
    totalInboxCount: Int = unreadCount
) {
    val palette = LocalNephoPalette.current
    val hasInboxItems = totalInboxCount > 0 || unreadCount > 0
    val lighting = rememberCalmBlueLightingState(active = hasInboxItems)
    val switcherShape = RoundedCornerShape(16.dp)

    Surface(
        shape = switcherShape,
        color = lighting.containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(switcherShape)
            .calmingLitBorder(
                accentColor = if (hasInboxItems) palette.calmBlue else palette.accent,
                cornerRadius = 16.dp,
                strokeWidth = if (hasInboxItems) 1.5.dp else 1.15.dp,
                intensity = if (hasInboxItems) CalmBorderIntensity.ACTIVE else CalmBorderIntensity.AMBIENT
            )
            .testTag("home_inbox_switcher")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .calmBlueLightingBackdrop(lighting, palette)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DynamicFeed,
                        contentDescription = null,
                        tint = if (hasInboxItems) lighting.headingColor else palette.accent,
                        modifier = Modifier.size(if (hasInboxItems) 22.dp else 18.dp)
                    )
                    Text(
                        text = stringResource(R.string.minimized_inbox_label),
                        style = if (hasInboxItems) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("inbox_section_heading")
                    )
                    if (hasInboxItems) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(lighting.headingColor)
                                .testTag("inbox_heading_glow_indicator")
                        )
                    }
                }
                if (onMinimize != null) {
                    SectionCollapseIconButton(
                        minimized = false,
                        sectionName = stringResource(R.string.home_section_inbox),
                        onToggle = onMinimize,
                        testTag = "minimize_section_inbox"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
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
    val palette = LocalNephoPalette.current
    val calmPrefs = LocalCalmVisualPrefs.current

    val buttonShape = RoundedCornerShape(12.dp)
    Surface(
        shape = buttonShape,
        color = if (selected) palette.accentContainer else palette.elevatedSurface.copy(alpha = 0.45f),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(buttonShape)
            .calmingLitBorder(
                accentColor = if (selected) palette.calmBlue else palette.accent,
                cornerRadius = 12.dp,
                strokeWidth = 1.dp,
                intensity = CalmBorderIntensity.SUBTLE
            )
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
                tint = if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeCount > 0) {
                Spacer(Modifier.width(6.dp))
                if (calmPrefs.hideUnreadCounts) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            .testTag("${testTag}_quiet_dot")
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) palette.accent else palette.border
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) palette.background else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
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
    unreadOnly: Boolean,
    hasLinksOnly: Boolean,
    starredOnly: Boolean,
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
            } else if (selectedAppFilter == null && !unreadOnly && !hasLinksOnly && !starredOnly && isNotificationAccessGranted) {
                count += 1 // empty_inbox_soothing_quote
            }
            count
        }
        PulseTab.LINKS_VAULT -> {
            1 + if (links.isEmpty()) 1 else links.size
        }
    }
}

private fun computeAppDrawerItemCount(
    groupedApps: Map<String, List<LauncherAppInfo>>
): Int {
    if (groupedApps.isEmpty()) return 2 // header + empty card
    var count = 1 // header
    groupedApps.values.forEach { apps ->
        count += 1 + apps.size
    }
    return count
}

private fun computeSectionStartIndices(
    sections: HomeSectionPrefs,
    orderedSectionIds: List<HomeSectionId>,
    inboxItemCount: Int,
    drawerItemCount: Int
): Map<HomeSectionId, Int> {
    val indices = mutableMapOf<HomeSectionId, Int>()
    var cursor = 0
    orderedSectionIds.forEach { sectionId ->
        if (!sections.isVisible(sectionId)) return@forEach
        indices[sectionId] = cursor
        cursor += when {
            sections.isMinimized(sectionId) -> 1
            sectionId == HomeSectionId.INBOX -> 1 + inboxItemCount
            sectionId == HomeSectionId.APPS -> drawerItemCount
            else -> 1
        }
    }
    return indices
}

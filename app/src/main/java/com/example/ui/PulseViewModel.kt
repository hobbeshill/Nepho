package com.example.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.AppShadePreference
import com.example.data.CachedGifAttachment
import com.example.data.ExtractedLinkItem
import com.example.data.PulseNotificationItem
import com.example.data.PulseRepository
import com.example.data.SessionMetrics
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.data.UndoDismissBatch
import com.example.data.local.CaptureDiagnosticEntity
import com.example.data.local.PulseDatabase
import com.example.launcher.ClockAlarmHelper
import com.example.launcher.LauncherAppCatalog
import com.example.launcher.LauncherAppInfo
import com.example.launcher.MediaPlaybackController
import com.example.launcher.MediaPlaybackState
import com.example.notifications.LocalPulseNotifier
import com.example.notifications.NephoNotificationListener
import com.example.updates.GitHubReleaseUpdater
import com.example.updates.UpdateCheckState
import com.example.widget.NephoWidgetUpdater
import com.example.worker.DigestSchedule
import com.example.worker.DigestWorker
import com.example.worker.RetentionPurgeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class PulseTab {
    FEED,
    LINKS_VAULT
}

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class ListenerHealthState(
    val isAccessGranted: Boolean = false,
    val isListenerConnected: Boolean = false,
    val lastConnectedAt: Long? = null,
    val hasPostNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true
)

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PulseRepository.getInstance(application)
    private val digestDao = PulseDatabase.getInstance(application).pulseDao()

    val digestSettings = digestDao.observeDigestSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val digestSummaries = digestDao.observeDigestSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _openDigest = MutableStateFlow(false)
    val openDigest = _openDigest.asStateFlow()

    private val _pendingWidgetThreadId = MutableStateFlow<String?>(null)
    val pendingWidgetThreadId: StateFlow<String?> = _pendingWidgetThreadId.asStateFlow()

    val updateGithubRepo: StateFlow<String> = repository.sessionPreferences.updateGithubRepoFlow
        .map { saved -> saved.ifBlank { BuildConfig.DEFAULT_GITHUB_REPO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuildConfig.DEFAULT_GITHUB_REPO)

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _selectedTab = MutableStateFlow(PulseTab.FEED)
    val selectedTab: StateFlow<PulseTab> = _selectedTab.asStateFlow()

    // Home Replacement / Launcher state
    private val mediaController = MediaPlaybackController(application)
    val mediaPlaybackState: StateFlow<MediaPlaybackState> = mediaController.state

    private val _installedApps = MutableStateFlow<List<LauncherAppInfo>>(emptyList())
    val installedApps: StateFlow<List<LauncherAppInfo>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val groupedInstalledApps: StateFlow<Map<String, List<LauncherAppInfo>>> = combine(
        _installedApps,
        _appSearchQuery
    ) { apps, query ->
        val filtered = LauncherAppCatalog.filterApps(apps, query)
        LauncherAppCatalog.groupAlphabetically(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val pinnedShortcutPackages: StateFlow<List<String>> =
        repository.sessionPreferences.pinnedShortcutsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedShortcutApps: StateFlow<List<LauncherAppInfo>> = combine(
        _installedApps,
        pinnedShortcutPackages
    ) { installed, pinnedPkgs ->
        val byPkg = installed.associateBy { it.packageName }
        pinnedPkgs.mapNotNull { byPkg[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isDefaultHome = MutableStateFlow(LauncherAppCatalog.isDefaultHomeApp(application))
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private val _nextAlarmSummary = MutableStateFlow(ClockAlarmHelper.readNextAlarmSummary(application))
    val nextAlarmSummary: StateFlow<String?> = _nextAlarmSummary.asStateFlow()

    private var packageReceiverRegistered = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val changedPkg = intent?.data?.schemeSpecificPart
            if (!changedPkg.isNullOrBlank()) {
                LauncherAppCatalog.invalidateIcon(changedPkg)
            }
            refreshInstalledApps()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _vaultSearchQuery = MutableStateFlow("")
    val vaultSearchQuery: StateFlow<String> = _vaultSearchQuery.asStateFlow()

    private val _selectedAppFilter = MutableStateFlow<SupportedApp?>(null)
    val selectedAppFilter: StateFlow<SupportedApp?> = _selectedAppFilter.asStateFlow()

    private val _unreadOnly = MutableStateFlow(false)
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    private val _hasLinksOnly = MutableStateFlow(false)
    val hasLinksOnly: StateFlow<Boolean> = _hasLinksOnly.asStateFlow()

    private val _starredOnly = MutableStateFlow(false)
    val starredOnly: StateFlow<Boolean> = _starredOnly.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(
        LocalPulseNotifier.hasNotificationPermission(application)
    )
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(
        NephoNotificationListener.isListenerEnabled(application)
    )
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(
        checkIgnoringBatteryOptimizations(application)
    )

    val listenerHealth: StateFlow<ListenerHealthState> = combine(
        _isNotificationAccessGranted,
        NephoNotificationListener.listenerConnectedFlow,
        NephoNotificationListener.lastConnectedAtFlow,
        _hasNotificationPermission,
        _isIgnoringBatteryOptimizations
    ) { accessGranted, connected, lastConnectedAt, notifPerm, ignoringBattery ->
        ListenerHealthState(
            isAccessGranted = accessGranted,
            isListenerConnected = connected,
            lastConnectedAt = lastConnectedAt,
            hasPostNotificationPermission = notifPerm,
            isIgnoringBatteryOptimizations = ignoringBattery
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListenerHealthState(
            isAccessGranted = _isNotificationAccessGranted.value,
            isListenerConnected = NephoNotificationListener.listenerConnectedFlow.value,
            lastConnectedAt = NephoNotificationListener.lastConnectedAtFlow.value,
            hasPostNotificationPermission = _hasNotificationPermission.value,
            isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.value
        )
    )

    val captureDiagnostics: StateFlow<List<CaptureDiagnosticEntity>> =
        repository.captureDiagnosticsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    private fun emitSnackbar(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        _snackbarEvents.tryEmit(SnackbarEvent(message, actionLabel, onAction))
    }

    val sessionOpenedAt: StateFlow<Long> = repository.sessionPreferences.sessionOpenedAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val bannerDismissed: StateFlow<Boolean> = repository.sessionPreferences.bannerDismissedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultShadeMode: StateFlow<ShadeMode> = repository.sessionPreferences.defaultShadeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShadeMode.SILENT)

    val shadePreferenceItems: StateFlow<List<AppShadePreference>> = combine(
        repository.sessionPreferences.perAppShadeModesFlow,
        repository.sessionPreferences.defaultShadeModeFlow,
        repository.sessionPreferences.observedAppsFlow,
        repository.sessionPreferences.optedOutAppsFlow
    ) { perAppModes, defaultMode, observedApps, optedOutApps ->
        val corePackages = SupportedApp.filterableApps.map { it.packageName }.toSet()
        val selfPkg = getApplication<Application>().packageName
        val coreItems = SupportedApp.filterableApps.map { app ->
            AppShadePreference(
                packageName = app.packageName,
                appLabel = app.displayName,
                isOptedIn = app.packageName !in optedOutApps,
                shadeMode = perAppModes[app.packageName] ?: defaultMode,
                isCoreApp = true
            )
        }
        val extraItems = observedApps.entries
            .filter { (pkg, _) -> pkg !in corePackages && pkg != selfPkg && pkg != SupportedApp.OTHER.packageName }
            .sortedBy { it.value.lowercase() }
            .map { (pkg, label) ->
                AppShadePreference(
                    packageName = pkg,
                    appLabel = label,
                    isOptedIn = pkg !in optedOutApps,
                    shadeMode = perAppModes[pkg] ?: defaultMode,
                    isCoreApp = false
                )
            }
        coreItems + extraItems
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allActiveNotifications: StateFlow<List<PulseNotificationItem>> =
        repository.activeNotificationsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredNotifications: StateFlow<List<PulseNotificationItem>> = combine(
        allActiveNotifications,
        _searchQuery,
        _selectedAppFilter,
        _unreadOnly,
        _hasLinksOnly,
        _starredOnly
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val items = values[0] as List<PulseNotificationItem>
        val query = values[1] as String
        val appFilter = values[2] as SupportedApp?
        val unread = values[3] as Boolean
        val hasLinks = values[4] as Boolean
        val starred = values[5] as Boolean
        val q = query.trim().lowercase()
        items.filter { item ->
            val matchesApp = appFilter == null || item.packageName == appFilter.packageName
            val matchesUnread = !unread || !item.isRead
            val matchesLinks = !hasLinks || item.urls.isNotEmpty()
            val matchesStarred = !starred || item.isStarred
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.body.lowercase().contains(q) ||
                item.appLabel.lowercase().contains(q)
            matchesApp && matchesUnread && matchesLinks && matchesStarred && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeLinks: StateFlow<List<ExtractedLinkItem>> = combine(
        repository.activeLinksFlow,
        _vaultSearchQuery
    ) { links, query ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            links
        } else {
            links.filter { link ->
                link.url.lowercase().contains(q) ||
                    link.domain.lowercase().contains(q) ||
                    link.senderTitle.lowercase().contains(q) ||
                    link.appLabel.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metrics: StateFlow<SessionMetrics> = combine(
        allActiveNotifications,
        repository.activeLinksFlow
    ) { notifications, links ->
        val unread = notifications.filter { !it.isRead }
        val unreadByApp = SupportedApp.filterableApps.associateWith { app ->
            unread.count { it.packageName == app.packageName }
        }
        SessionMetrics(
            totalInWindow = notifications.size,
            unreadTotal = unread.size,
            unreadByApp = unreadByApp,
            totalLinks = links.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionMetrics())

    init {
        RetentionPurgeWorker.schedulePeriodicPurge(application)
        registerPackageChangeReceiver(application)
        refreshLauncherState()
        viewModelScope.launch {
            repository.initializeSession()
            val settings = digestDao.getDigestSettings()
            if (settings?.enabled == true) {
                DigestWorker.schedule(application, settings.nextEnd)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                _nextAlarmSummary.value = ClockAlarmHelper.readNextAlarmSummary(getApplication())
                val purged = repository.purgeExpired()
                if (purged > 0) {
                    emitSnackbar("24h rolling window purged $purged expired entries.")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController.release()
        if (packageReceiverRegistered) {
            runCatching {
                getApplication<Application>().unregisterReceiver(packageChangeReceiver)
            }
            packageReceiverRegistered = false
        }
    }

    private fun registerPackageChangeReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                packageChangeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            packageReceiverRegistered = true
        }.onFailure {
            Log.w(TAG, "Unable to register package change receiver", it)
        }
    }

    fun refreshLauncherState() {
        val ctx = getApplication<Application>()
        _isDefaultHome.value = LauncherAppCatalog.isDefaultHomeApp(ctx)
        _nextAlarmSummary.value = ClockAlarmHelper.readNextAlarmSummary(ctx)
        mediaController.refresh()
        refreshInstalledApps()
    }

    private fun refreshInstalledApps() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val apps = withContext(Dispatchers.IO) {
                LauncherAppCatalog.loadLaunchableApps(ctx)
            }
            _installedApps.value = apps
            val customized = repository.sessionPreferences.shortcutsCustomizedFlow.first()
            val currentPinned = repository.sessionPreferences.pinnedShortcutsFlow.first()
            if (!customized && currentPinned.isEmpty() && apps.isNotEmpty()) {
                val seeded = LauncherAppCatalog.seedDefaultShortcuts(apps)
                if (seeded.isNotEmpty()) {
                    repository.sessionPreferences.setPinnedShortcuts(seeded, markCustomized = false)
                }
            }
        }
    }

    fun updateAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun launchInstalledApp(app: LauncherAppInfo) {
        val ctx = getApplication<Application>()
        if (!LauncherAppCatalog.launchApp(ctx, app)) {
            emitSnackbar(ctx.getString(R.string.open_app_unavailable, app.label))
        }
    }

    fun togglePinnedShortcut(app: LauncherAppInfo) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val current = repository.sessionPreferences.pinnedShortcutsFlow.first().toMutableList()
            if (app.packageName in current) {
                current.remove(app.packageName)
                repository.sessionPreferences.setPinnedShortcuts(current, markCustomized = true)
                emitSnackbar(ctx.getString(R.string.drawer_unpinned_toast, app.label))
            } else {
                if (current.size >= LauncherAppCatalog.MAX_PINNED_SHORTCUTS) {
                    emitSnackbar(
                        ctx.getString(
                            R.string.drawer_pin_limit_toast,
                            LauncherAppCatalog.MAX_PINNED_SHORTCUTS
                        )
                    )
                    return@launch
                }
                current.add(app.packageName)
                repository.sessionPreferences.setPinnedShortcuts(current, markCustomized = true)
                emitSnackbar(ctx.getString(R.string.drawer_pinned_toast, app.label))
            }
        }
    }

    fun movePinnedShortcut(packageName: String, delta: Int) {
        viewModelScope.launch {
            val current = repository.sessionPreferences.pinnedShortcutsFlow.first().toMutableList()
            val index = current.indexOf(packageName)
            if (index < 0) return@launch
            val target = (index + delta).coerceIn(0, current.lastIndex)
            if (target == index) return@launch
            val item = current.removeAt(index)
            current.add(target, item)
            repository.sessionPreferences.setPinnedShortcuts(current, markCustomized = true)
        }
    }

    fun openDefaultHomeSettings() {
        val ctx = getApplication<Application>()
        if (!LauncherAppCatalog.openHomeSettings(ctx)) {
            emitSnackbar(ctx.getString(R.string.default_home_settings_unavailable))
        }
    }

    fun setQuickAlarm(hour: Int, minute: Int, label: String?) {
        val ctx = getApplication<Application>()
        val launched = ClockAlarmHelper.launchSetAlarm(ctx, hour, minute, label)
        if (launched) {
            val formatted = ClockAlarmHelper.formatAlarmTime(
                hour = hour,
                minute = minute,
                is24Hour = DateFormat.is24HourFormat(ctx)
            )
            emitSnackbar(ctx.getString(R.string.alarm_set_success, formatted))
            viewModelScope.launch {
                delay(1_200L)
                _nextAlarmSummary.value = ClockAlarmHelper.readNextAlarmSummary(ctx)
            }
        } else {
            emitSnackbar(ctx.getString(R.string.alarm_app_unavailable))
        }
    }

    fun openClockApp() {
        val ctx = getApplication<Application>()
        if (!ClockAlarmHelper.openClockApp(ctx)) {
            emitSnackbar(ctx.getString(R.string.alarm_app_unavailable))
        }
    }

    fun mediaTogglePlayPause() {
        mediaController.togglePlayPause()
    }

    fun mediaSkipNext() {
        mediaController.skipNext()
    }

    fun mediaSkipPrevious() {
        mediaController.skipPrevious()
    }

    fun openMusicApp() {
        val ctx = getApplication<Application>()
        val fallbackPkg = LauncherAppCatalog.findDefaultMusicPackage(ctx, _installedApps.value)
        if (!mediaController.openActiveMediaApp(ctx, fallbackPkg)) {
            emitSnackbar(ctx.getString(R.string.music_app_unavailable))
        }
    }

    fun selectTab(tab: PulseTab) {
        _selectedTab.value = tab
    }

    fun requestOpenDigest() {
        _openDigest.value = true
    }

    fun clearOpenDigest() {
        _openDigest.value = false
    }

    fun openThreadFromWidget(threadId: String) {
        if (threadId.isBlank()) return
        _selectedTab.value = PulseTab.FEED
        _selectedAppFilter.value = null
        _unreadOnly.value = false
        _hasLinksOnly.value = false
        _starredOnly.value = false
        _searchQuery.value = ""
        _pendingWidgetThreadId.value = threadId
    }

    fun clearPendingWidgetThreadId() {
        _pendingWidgetThreadId.value = null
    }

    fun applyAppFilterFromWidget(
        packageName: String,
        appLabel: String? = null,
        unreadOnly: Boolean = true
    ) {
        _selectedTab.value = PulseTab.FEED
        _hasLinksOnly.value = false
        _starredOnly.value = false
        _unreadOnly.value = unreadOnly
        val coreApp = SupportedApp.filterableApps.firstOrNull { it.packageName == packageName }
        if (coreApp != null) {
            _selectedAppFilter.value = coreApp
            _searchQuery.value = ""
        } else {
            _selectedAppFilter.value = null
            _searchQuery.value = appLabel?.trim().orEmpty()
        }
    }

    fun requestPinHomeWidget() {
        val ctx = getApplication<Application>()
        val requested = NephoWidgetUpdater.requestPinWidget(ctx)
        emitSnackbar(
            ctx.getString(
                if (requested) R.string.widget_pin_requested else R.string.widget_pin_unavailable
            )
        )
    }

    fun checkForAppUpdates(rawRepoInput: String) {
        val ctx = getApplication<Application>()
        val normalized = GitHubReleaseUpdater.normalizeRepoSlug(rawRepoInput)
        if (normalized == null) {
            _updateCheckState.value = UpdateCheckState.Error(
                ctx.getString(R.string.updates_invalid_repo)
            )
            return
        }
        viewModelScope.launch {
            repository.sessionPreferences.setUpdateGithubRepo(normalized)
            _updateCheckState.value = UpdateCheckState.Checking
            _updateCheckState.value = GitHubReleaseUpdater.checkLatestRelease(
                repoSlug = normalized,
                currentVersionName = BuildConfig.VERSION_NAME
            )
        }
    }

    fun openExternalUrl(url: String) {
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open URL $url", e)
            emitSnackbar(ctx.getString(R.string.updates_browser_unavailable))
        }
    }

    fun copyToClipboard(label: String, text: String, confirmationMessage: String) {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        emitSnackbar(confirmationMessage)
    }

    fun setDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val settings = digestDao.setDigestEnabled(
                    enabled,
                    DigestSchedule.nextBoundary(System.currentTimeMillis())
                )
                if (enabled) DigestWorker.schedule(context, settings.nextEnd)
                else DigestWorker.cancel(context)
            } catch (e: Exception) {
                Log.e("PulseViewModel", "Could not update digest settings", e)
                emitSnackbar(getApplication<Application>().getString(R.string.digest_settings_failed))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateVaultSearchQuery(query: String) {
        _vaultSearchQuery.value = query
    }

    fun selectAppFilter(app: SupportedApp?) {
        _selectedAppFilter.value = if (_selectedAppFilter.value == app) null else app
    }

    fun toggleUnreadOnly() {
        _unreadOnly.value = !_unreadOnly.value
    }

    fun toggleHasLinksOnly() {
        _hasLinksOnly.value = !_hasLinksOnly.value
    }

    fun toggleStarredOnly() {
        _starredOnly.value = !_starredOnly.value
    }

    fun toggleNotificationStar(key: String) {
        viewModelScope.launch {
            repository.toggleNotificationStar(key)
        }
    }

    fun toggleNotificationStarFromSwipe(key: String) {
        viewModelScope.launch {
            val isNowStarred = repository.toggleNotificationStar(key)
            emitSnackbar(
                if (isNowStarred) {
                    "Starred • Protected from swipe dismiss and 24h purge."
                } else {
                    "Star removed."
                }
            )
        }
    }

    fun toggleLinkStar(linkId: String, currentlyStarred: Boolean) {
        viewModelScope.launch {
            repository.toggleLinkStar(linkId, currentlyStarred)
        }
    }

    fun scheduleReminder(key: String, triggerAtMillis: Long, note: String?) {
        viewModelScope.launch {
            repository.scheduleReminder(key, triggerAtMillis, note)
            // Only surface a message when the reminder can't actually alert the user
            if (!LocalPulseNotifier.hasNotificationPermission(getApplication())) {
                emitSnackbar("Reminder saved, but enable Local Alerts to receive its push notification.")
            }
        }
    }

    fun clearReminder(key: String) {
        viewModelScope.launch {
            repository.cancelReminder(key)
        }
    }

    fun dismissBanner() {
        viewModelScope.launch {
            repository.sessionPreferences.setBannerDismissed(true)
        }
    }

    fun refreshAccessAndPermissions() {
        val ctx = getApplication<Application>()
        val previousAccess = _isNotificationAccessGranted.value
        val currentAccess = NephoNotificationListener.isListenerEnabled(ctx)
        _hasNotificationPermission.value = LocalPulseNotifier.hasNotificationPermission(ctx)
        _isNotificationAccessGranted.value = currentAccess
        _isIgnoringBatteryOptimizations.value = checkIgnoringBatteryOptimizations(ctx)
        if (previousAccess != currentAccess) {
            NephoWidgetUpdater.requestUpdate(ctx)
        }
    }

    fun requestListenerRebind() {
        val ctx = getApplication<Application>()
        val requested = NephoNotificationListener.requestRebindService(ctx)
        refreshAccessAndPermissions()
        emitSnackbar(
            ctx.getString(
                if (requested) R.string.diagnostics_rebind_sent else R.string.diagnostics_rebind_failed
            )
        )
    }

    fun openBatteryOptimizationSettings() {
        val ctx = getApplication<Application>()
        val primaryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ctx.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(primaryIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Primary battery settings unavailable, falling back to app details", e)
            try {
                ctx.startActivity(fallbackIntent)
            } catch (fallbackError: Exception) {
                Log.w(TAG, "App details settings unavailable", fallbackError)
                emitSnackbar(ctx.getString(R.string.diagnostics_battery_settings_unavailable))
            }
        }
    }

    fun clearCaptureDiagnostics() {
        viewModelScope.launch {
            repository.clearCaptureDiagnostics()
            emitSnackbar(getApplication<Application>().getString(R.string.diagnostics_cleared))
        }
    }

    private fun checkIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(true)
    }

    fun refreshNotificationPermission() {
        val granted = LocalPulseNotifier.hasNotificationPermission(getApplication())
        _hasNotificationPermission.value = granted
        viewModelScope.launch {
            repository.sessionPreferences.setLocalAlertsEnabled(granted)
        }
        if (granted) {
            emitSnackbar("Nepho shade alerts enabled.")
        }
    }

    fun setAppOptedIn(packageName: String, optedIn: Boolean) {
        viewModelScope.launch {
            repository.sessionPreferences.setAppOptedIn(packageName, optedIn)
        }
    }

    fun setAppShadeMode(packageName: String, mode: ShadeMode) {
        viewModelScope.launch {
            repository.sessionPreferences.setAppShadeMode(packageName, mode)
            when (mode) {
                ShadeMode.BLOCKED -> {
                    // Immediately clear anything the blocked app already has in the shade
                    NephoNotificationListener.cancelActiveNotificationsForPackage(packageName)
                    emitSnackbar(
                        "App blocked in Nepho. Opening system settings so you can turn its notifications off there too."
                    )
                    openSystemNotificationSettings(packageName)
                }
                ShadeMode.SILENT -> {
                    emitSnackbar(
                        "App silenced in Nepho (feed only). Opening system settings so you can mark it Silent there too."
                    )
                    openSystemNotificationSettings(packageName)
                }
                ShadeMode.NEPHO_ALERT -> Unit
            }
        }
    }

    /**
     * Nepho cannot rewrite another app's notification settings directly, so syncing means
     * deep-linking to that app's system notification settings screen for the user to confirm.
     */
    fun openSystemNotificationSettings(packageName: String) {
        val ctx = getApplication<Application>()
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            @Suppress("DEPRECATION")
            android.content.Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                .putExtra("app_package", packageName)
                .putExtra(
                    "app_uid",
                    runCatching { ctx.packageManager.getApplicationInfo(packageName, 0).uid }.getOrDefault(0)
                )
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open system notification settings for $packageName", e)
            emitSnackbar("Couldn't open system notification settings for this app.")
        }
    }

    fun setDefaultShadeMode(mode: ShadeMode) {
        viewModelScope.launch {
            repository.sessionPreferences.setDefaultShadeMode(mode)
        }
    }

    fun ingestSharedOrCustomMessage(
        app: SupportedApp,
        title: String,
        body: String,
        sourceChannel: String = "SHARE_SHEET"
    ) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val key = "custom_${UUID.randomUUID().toString().take(8)}"
            val notifId = (now % 100000).toInt() + 500
            val cleanTitle = title.ifBlank { "${app.displayName} Shared Note" }
            val cleanBody = body.ifBlank { cleanTitle }
            val secondary = when (app) {
                SupportedApp.OUTLOOK, SupportedApp.GMAIL -> listOf("Archive", "Delete")
                else -> listOf("Mark Read", "Mute")
            }

            val result = repository.ingestNotification(
                key = key,
                notificationId = notifId,
                packageName = app.packageName,
                appLabel = app.displayName,
                title = cleanTitle,
                body = cleanBody,
                postTime = now,
                canReply = true,
                secondaryActions = secondary,
                sourceChannel = sourceChannel
            )

            if (repository.shouldPostShadeAlert(app.packageName)) {
                LocalPulseNotifier.postInteractiveLocalNotification(
                    context = getApplication(),
                    repository = repository,
                    key = key,
                    notificationId = notifId,
                    appLabel = app.displayName,
                    title = cleanTitle,
                    body = cleanBody,
                    canReply = true,
                    secondaryActions = secondary
                )
            }

            val linkMsg = if (result.extractedLinkCount > 0) {
                " • ${result.extractedLinkCount} link(s) saved to Vault"
            } else {
                ""
            }
            emitSnackbar("Captured ${app.displayName} item$linkMsg.")
        }
    }

    fun markAsRead(key: String) {
        viewModelScope.launch {
            repository.markAsRead(key)
        }
    }

    fun canSendGifReply(key: String): Boolean =
        repository.canSendGifReply(key)

    fun shouldDirectShareGif(key: String, packageName: String): Boolean =
        repository.shouldDirectShareGif(key, packageName)

    suspend fun stageGifAttachment(sourceUri: Uri): CachedGifAttachment? =
        withContext(Dispatchers.IO) {
            repository.stageGifAttachment(sourceUri)
        }

    fun notifyGifLoadFailed() {
        emitSnackbar(getApplication<Application>().getString(R.string.gif_cache_failed))
    }

    fun sendInlineReply(
        key: String,
        replyText: String,
        gifUri: Uri? = null,
        cachedGif: CachedGifAttachment? = null
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.sendReplyWithResult(
                    key = key,
                    replyText = replyText,
                    gifAttachment = cachedGif,
                    gifUri = gifUri
                )
            }
            if (gifUri != null || cachedGif != null) {
                val ctx = getApplication<Application>()
                when {
                    !result.success -> emitSnackbar(ctx.getString(R.string.gif_send_failed))
                    result.usedChooserFallback -> emitSnackbar(ctx.getString(R.string.gif_share_chooser_hint))
                }
            }
        }
    }

    fun dismissNotification(key: String) {
        viewModelScope.launch {
            repository.dismissNotification(key)
        }
    }

    fun dismissNotificationWithUndo(key: String) {
        viewModelScope.launch {
            val batch = repository.dismissNotificationWithUndo(key) ?: return@launch
            emitSnackbar(
                message = "Alert dismissed",
                actionLabel = "Undo",
                onAction = { restoreDismissedBatch(batch) }
            )
        }
    }

    fun notifyStarredSwipeProtected() {
        emitSnackbar("Starred alert is protected. Swipe left to unstar first.")
    }

    fun markAppGroupAsRead(packageName: String, appLabel: String) {
        viewModelScope.launch {
            val updated = repository.markPackageAsRead(packageName)
            if (updated > 0) {
                emitSnackbar("Marked $updated $appLabel alert${if (updated == 1) "" else "s"} as read.")
            }
        }
    }

    fun clearAppGroupWithUndo(packageName: String, appLabel: String) {
        viewModelScope.launch {
            val batch = repository.dismissUnstarredForPackageWithUndo(packageName)
            when {
                batch.items.isNotEmpty() -> {
                    val count = batch.items.size
                    val starredSuffix = if (batch.keptStarredCount > 0) {
                        " (${batch.keptStarredCount} starred kept)"
                    } else {
                        ""
                    }
                    emitSnackbar(
                        message = "Cleared $count $appLabel alert${if (count == 1) "" else "s"}$starredSuffix",
                        actionLabel = "Undo",
                        onAction = { restoreDismissedBatch(batch) }
                    )
                }
                batch.keptStarredCount > 0 -> {
                    emitSnackbar("All $appLabel alerts are starred and protected.")
                }
            }
        }
    }

    private fun restoreDismissedBatch(batch: UndoDismissBatch) {
        viewModelScope.launch {
            repository.restoreDismissedBatch(batch)
        }
    }

    fun executeSecondaryAction(key: String, actionLabel: String) {
        viewModelScope.launch {
            repository.executeSecondaryAction(key, actionLabel)
        }
    }

    fun markThreadAsRead(keys: List<String>) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            keys.forEach { repository.markAsRead(it) }
        }
    }

    fun openSourceApp(item: PulseNotificationItem) {
        if (!repository.openSourceConversation(item.key)) {
            emitSnackbar(
                "The original ${item.appLabel} conversation is no longer available. Open it in ${item.appLabel}."
            )
        }
    }

    fun openSelectedApp(app: SupportedApp) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            emitSnackbar(context.getString(R.string.open_app_unavailable, app.displayName))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot launch ${app.packageName}", e)
            emitSnackbar(context.getString(R.string.open_app_unavailable, app.displayName))
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot launch ${app.packageName}", e)
            emitSnackbar(context.getString(R.string.open_app_unavailable, app.displayName))
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            repository.resetSession()
            _selectedAppFilter.value = null
            _unreadOnly.value = false
            _hasLinksOnly.value = false
            _starredOnly.value = false
            _searchQuery.value = ""
            _vaultSearchQuery.value = ""
            emitSnackbar("Session reset. Starred items preserved; 24h window restarted.")
        }
    }

    companion object {
        private const val TAG = "PulseViewModel"
    }
}

package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppShadePreference
import com.example.data.ExtractedLinkItem
import com.example.data.PulseNotificationItem
import com.example.data.PulseRepository
import com.example.data.SessionMetrics
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.data.UndoDismissBatch
import com.example.notifications.LocalPulseNotifier
import com.example.notifications.NephoNotificationListener
import com.example.worker.RetentionPurgeWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PulseRepository.getInstance(application)

    private val _selectedTab = MutableStateFlow(PulseTab.FEED)
    val selectedTab: StateFlow<PulseTab> = _selectedTab.asStateFlow()

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
        viewModelScope.launch {
            repository.initializeSession()
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val purged = repository.purgeExpired()
                if (purged > 0) {
                    emitSnackbar("24h rolling window purged $purged expired entries.")
                }
            }
        }
    }

    fun selectTab(tab: PulseTab) {
        _selectedTab.value = tab
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
        _hasNotificationPermission.value = LocalPulseNotifier.hasNotificationPermission(ctx)
        _isNotificationAccessGranted.value = NephoNotificationListener.isListenerEnabled(ctx)
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

    fun sendInlineReply(key: String, replyText: String) {
        viewModelScope.launch {
            repository.sendInlineReply(key, replyText)
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

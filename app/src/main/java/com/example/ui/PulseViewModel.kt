package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SmartDigestService
import com.example.data.AppShadePreference
import com.example.data.DigestResult
import com.example.data.ExtractedLinkItem
import com.example.data.MockNotificationFactory
import com.example.data.PulseNotificationItem
import com.example.data.PulseRepository
import com.example.data.SessionMetrics
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import com.example.notifications.LocalPulseNotifier
import com.example.notifications.NephoNotificationListener
import com.example.worker.RetentionPurgeWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    SMART_DIGEST,
    LINKS_VAULT
}

sealed interface DigestUiState {
    data object Idle : DigestUiState
    data object Loading : DigestUiState
    data class Success(val result: DigestResult) : DigestUiState
    data class Error(val message: String) : DigestUiState
}

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PulseRepository.getInstance(application)
    private val smartDigestService = SmartDigestService()

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

    private val _digestState = MutableStateFlow<DigestUiState>(DigestUiState.Idle)
    val digestState: StateFlow<DigestUiState> = _digestState.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(
        LocalPulseNotifier.hasNotificationPermission(application)
    )
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(
        NephoNotificationListener.isListenerEnabled(application)
    )
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

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
            repository.latestDigestFlow.collect { saved ->
                if (saved != null && _digestState.value is DigestUiState.Idle) {
                    _digestState.value = DigestUiState.Success(saved)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val purged = repository.purgeExpired()
                if (purged > 0) {
                    _snackbarEvents.tryEmit("24h rolling window purged $purged expired entries.")
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
            val isNowStarred = repository.toggleNotificationStar(key)
            _snackbarEvents.tryEmit(
                if (isNowStarred) {
                    "Starred! Protected from 24h auto-purge and session resets."
                } else {
                    "Star removed. Standard 24h retention applies."
                }
            )
        }
    }

    fun toggleLinkStar(linkId: String, currentlyStarred: Boolean) {
        viewModelScope.launch {
            val isNowStarred = repository.toggleLinkStar(linkId, currentlyStarred)
            _snackbarEvents.tryEmit(
                if (isNowStarred) {
                    "Link starred! Protected from 24h auto-purge."
                } else {
                    "Link star removed."
                }
            )
        }
    }

    fun scheduleReminder(key: String, triggerAtMillis: Long, note: String?) {
        viewModelScope.launch {
            repository.scheduleReminder(key, triggerAtMillis, note)
            val timeStr = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(triggerAtMillis))
            val suffix = if (!LocalPulseNotifier.hasNotificationPermission(getApplication())) {
                " (Enable Local Alerts to receive push notifications)"
            } else {
                ""
            }
            _snackbarEvents.tryEmit("Reminder set for $timeStr$suffix.")
        }
    }

    fun clearReminder(key: String) {
        viewModelScope.launch {
            repository.cancelReminder(key)
            _snackbarEvents.tryEmit("Reminder cleared.")
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
            _snackbarEvents.tryEmit("Nepho shade alerts enabled.")
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
        }
    }

    fun setDefaultShadeMode(mode: ShadeMode) {
        viewModelScope.launch {
            repository.sessionPreferences.setDefaultShadeMode(mode)
        }
    }

    fun simulateIncomingBatch(includeExpiredTest: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val specs = MockNotificationFactory.createRealisticBatch(now).toMutableList()
            if (includeExpiredTest) {
                specs.add(MockNotificationFactory.createExpiredTestNotification(now))
            }

            var totalLinks = 0
            var totalPurged = 0
            var ingestedActive = 0
            var skippedOptedOut = 0
            val ctx = getApplication<Application>()

            specs.forEach { spec ->
                val isExpiredSpec = spec.postTime < now - PulseRepository.RETENTION_WINDOW_MS
                if (!isExpiredSpec && !repository.isAppOptedIn(spec.app.packageName)) {
                    skippedOptedOut++
                    return@forEach
                }

                val result = repository.ingestNotification(
                    key = spec.key,
                    notificationId = spec.notificationId,
                    packageName = spec.app.packageName,
                    appLabel = spec.app.displayName,
                    title = spec.title,
                    body = spec.body,
                    postTime = spec.postTime,
                    canReply = spec.canReply,
                    secondaryActions = spec.secondaryActions,
                    isSimulated = true,
                    sourceChannel = "SIMULATED"
                )
                if (!isExpiredSpec) {
                    ingestedActive++
                }
                totalLinks += result.extractedLinkCount
                totalPurged += result.purgedExpiredCount

                if (!isExpiredSpec && repository.shouldPostShadeAlert(spec.app.packageName)) {
                    LocalPulseNotifier.postInteractiveLocalNotification(
                        context = ctx,
                        repository = repository,
                        key = spec.key,
                        notificationId = spec.notificationId,
                        appLabel = spec.app.displayName,
                        title = spec.title,
                        body = spec.body,
                        canReply = spec.canReply,
                        secondaryActions = spec.secondaryActions
                    )
                }
            }

            val optedOutNote = if (skippedOptedOut > 0) " ($skippedOptedOut skipped: opted out)" else ""
            val message = if (includeExpiredTest) {
                "Simulated $ingestedActive active alerts + 25h expired test item$optedOutNote ($totalPurged expired auto-purged)."
            } else {
                "Simulated $ingestedActive notifications$optedOutNote ($totalLinks URLs captured into Links Vault)."
            }
            _snackbarEvents.tryEmit(message)
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
                isSimulated = false,
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
            _snackbarEvents.tryEmit("Captured ${app.displayName} item$linkMsg.")
        }
    }

    fun markAsRead(key: String) {
        viewModelScope.launch {
            repository.markAsRead(key)
        }
    }

    fun sendInlineReply(key: String, replyText: String) {
        viewModelScope.launch {
            val ok = repository.sendInlineReply(key, replyText)
            if (ok) {
                _snackbarEvents.tryEmit("Reply sent and recorded in thread.")
            }
        }
    }

    fun dismissNotification(key: String) {
        viewModelScope.launch {
            repository.dismissNotification(key)
            _snackbarEvents.tryEmit("Alert dismissed.")
        }
    }

    fun executeSecondaryAction(key: String, actionLabel: String) {
        viewModelScope.launch {
            repository.executeSecondaryAction(key, actionLabel)
            _snackbarEvents.tryEmit("Action \"$actionLabel\" executed.")
        }
    }

    fun generateAiSummary() {
        viewModelScope.launch {
            val messages = repository.getSnapshotInWindow()
            if (messages.isEmpty()) {
                _snackbarEvents.tryEmit("No messages in the 24h window to summarize.")
                return@launch
            }
            _digestState.value = DigestUiState.Loading
            try {
                val result = smartDigestService.generateAiDigest(messages)
                repository.saveDigestResult(result)
                _digestState.value = DigestUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini AI summary timed out", e)
                _digestState.value = DigestUiState.Error(
                    "AI digest request timed out after 60 seconds. You can retry or generate an offline rule-based digest."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini AI summary failed", e)
                _digestState.value = DigestUiState.Error(
                    e.localizedMessage ?: "Failed to reach Firebase AI Logic."
                )
            }
        }
    }

    fun generateLocalFallbackSummary() {
        viewModelScope.launch {
            val messages = repository.getSnapshotInWindow()
            if (messages.isEmpty()) {
                _snackbarEvents.tryEmit("No messages in the 24h window to summarize.")
                return@launch
            }
            val result = smartDigestService.generateLocalRuleBasedDigest(messages)
            repository.saveDigestResult(result)
            _digestState.value = DigestUiState.Success(result)
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            repository.resetSession()
            _digestState.value = DigestUiState.Idle
            _selectedAppFilter.value = null
            _unreadOnly.value = false
            _hasLinksOnly.value = false
            _starredOnly.value = false
            _searchQuery.value = ""
            _vaultSearchQuery.value = ""
            _snackbarEvents.tryEmit("Session reset. Starred items preserved; 24h window restarted.")
        }
    }

    fun notifyLinkCopied() {
        _snackbarEvents.tryEmit("Link copied to clipboard.")
    }

    companion object {
        private const val TAG = "PulseViewModel"
    }
}

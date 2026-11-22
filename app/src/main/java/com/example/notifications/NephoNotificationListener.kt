package com.example.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.CaptureOutcome
import com.example.data.NativeReplyHandle
import com.example.data.NotificationImageStore
import com.example.data.PulseRepository
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class ExtractedNotificationContent(
    val title: String,
    val body: String,
    val hasDirectBodyText: Boolean,
    val hasMedia: Boolean
)

class NephoNotificationListener : NotificationListenerService() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serializedDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val serviceScope = CoroutineScope(SupervisorJob() + serializedDispatcher)
    private val selfCanceledPostTimes = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = WeakReference(this)
        val connectedAt = System.currentTimeMillis()
        _listenerConnected.value = true
        _lastConnectedAt.value = connectedAt
        ListenerRecoveryCoordinator.onListenerConnected(applicationContext)

        val repository = PulseRepository.getInstance(applicationContext)
        val currentActive = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())

        serviceScope.launch {
            repository.recordCaptureDiagnostic(
                packageName = applicationContext.packageName,
                appLabel = "Nepho",
                titlePreview = "Listener connected",
                outcome = CaptureOutcome.LISTENER_CONNECTED,
                detail = "Active shade sweep checking ${currentActive.size} notification(s) posted since session start.",
                timestamp = connectedAt
            )
            catchUpActiveNotifications(repository, currentActive)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        _listenerConnected.value = false
        ListenerRecoveryCoordinator.onYellowStatusDetected(
            context = applicationContext,
            trigger = RecoveryTrigger.DISCONNECT
        )
        val repository = PulseRepository.getInstance(applicationContext)
        serviceScope.launch {
            repository.recordCaptureDiagnostic(
                packageName = applicationContext.packageName,
                appLabel = "Nepho",
                titlePreview = "Listener disconnected",
                outcome = CaptureOutcome.LISTENER_DISCONNECTED,
                detail = "Android unbound the notification listener. Automatic recovery is running and will catch up post-session alerts on reconnect."
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == applicationContext.packageName) return
        val activeSnapshot = runCatching { activeNotifications?.toList() }.getOrNull()
        val repository = PulseRepository.getInstance(applicationContext)
        serviceScope.launch {
            processPostedNotification(
                repository = repository,
                sbn = sbn,
                isCatchUp = false,
                activeSnapshot = activeSnapshot
            )
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null) return

        val repository = PulseRepository.getInstance(applicationContext)
        serviceScope.launch {
            handleNotificationRemoved(repository, sbn, reason)
        }
    }

    internal suspend fun handleNotificationRemoved(
        repository: PulseRepository,
        sbn: StatusBarNotification,
        reason: Int
    ) {
        // Sync user actions on Nepho's own re-posted shade alerts back into the Feed
        if (sbn.packageName == applicationContext.packageName) {
            val nephoKey = sbn.notification?.extras?.getString(LocalPulseNotifier.EXTRA_NEPHO_KEY)
                ?: return
            when {
                reason in NEPHO_SHADE_DISMISS_REASONS -> {
                    val existing = repository.getNotificationByKey(nephoKey)
                    val synced = repository.syncDismissalFromSystem(key = nephoKey)
                    if (synced && existing != null) {
                        repository.recordCaptureDiagnostic(
                            packageName = existing.packageName,
                            appLabel = existing.appLabel,
                            titlePreview = existing.title,
                            outcome = CaptureOutcome.SYNCED_REMOVAL,
                            detail = "Dismissed in Nepho to match shade removal (${removalReasonLabel(reason)})."
                        )
                    }
                }
                reason == REASON_CLICK -> {
                    repository.markAsRead(nephoKey)
                }
            }
            return
        }

        // Skip non-syncable removals (e.g. REASON_LISTENER_CANCEL, system housekeeping)
        if (reason !in SYNCABLE_REMOVAL_REASONS) {
            selfCanceledPostTimes.remove(sbn.key, sbn.postTime)
            return
        }

        // Guard against race conditions where Android or the source app fires a syncable
        // removal callback immediately after Nepho itself suppressed this exact postTime.
        if (selfCanceledPostTimes.remove(sbn.key, sbn.postTime)) {
            val appLabel = resolveAppLabel(sbn.packageName)
            val titlePreview = sbn.notification?.extras
                ?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                .orEmpty()
                .ifEmpty { appLabel }
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = titlePreview,
                outcome = CaptureOutcome.SELF_CANCEL_IGNORED,
                detail = "Ignored removal callback (${removalReasonLabel(reason)}) matching Nepho's own shade suppression."
            )
            return
        }

        val synced = repository.syncDismissalFromSystem(
            key = sbn.key,
            removedPostTime = sbn.postTime
        )
        if (synced) {
            val appLabel = resolveAppLabel(sbn.packageName)
            val titlePreview = sbn.notification?.extras
                ?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                .orEmpty()
                .ifEmpty { appLabel }
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = titlePreview,
                outcome = CaptureOutcome.SYNCED_REMOVAL,
                detail = "Dismissed in Nepho to match removal outside Nepho (${removalReasonLabel(reason)})."
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        _listenerConnected.value = false
        selfCanceledPostTimes.clear()
        serviceScope.cancel()
    }

    internal suspend fun catchUpActiveNotifications(
        repository: PulseRepository,
        activeList: List<StatusBarNotification>
    ) {
        val sessionOpenedAt = repository.sessionPreferences.ensureSessionOpenedAt()
        val appPkg = applicationContext.packageName
        for (sbn in activeList.sortedBy { it.postTime }) {
            if (sbn.packageName == appPkg) continue
            if (sbn.postTime < sessionOpenedAt) continue
            val existing = repository.getNotificationByKey(sbn.key)
            if (existing != null && existing.postTime >= sbn.postTime) continue
            processPostedNotification(
                repository = repository,
                sbn = sbn,
                isCatchUp = true,
                activeSnapshot = activeList
            )
        }
    }

    internal suspend fun processPostedNotification(
        repository: PulseRepository,
        sbn: StatusBarNotification,
        isCatchUp: Boolean,
        activeSnapshot: List<StatusBarNotification>?
    ) {
        val notification = sbn.notification ?: return
        // Ignore ongoing/persistent system notifications (foreground services, media players, charging)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (notification.flags and Notification.FLAG_NO_CLEAR) != 0
        ) return

        val appLabel = resolveAppLabel(sbn.packageName)
        val extracted = extractNotificationContent(notification, appLabel)

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            val hasSiblingChildren = activeSnapshot?.any { other ->
                other.key != sbn.key &&
                    other.packageName == sbn.packageName &&
                    (other.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    (notification.group == null || other.notification.group == notification.group)
            } == true
            if (hasSiblingChildren || !extracted.hasDirectBodyText) {
                repository.recordCaptureDiagnostic(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    titlePreview = extracted.title,
                    outcome = CaptureOutcome.SKIPPED_GROUP_SUMMARY,
                    detail = if (hasSiblingChildren) {
                        "Skipped redundant group summary wrapper because child message notifications exist."
                    } else {
                        "Skipped group summary without direct message content."
                    }
                )
                return
            }
        }

        if (extracted.title.isEmpty() && extracted.body.isEmpty() && !extracted.hasMedia) return

        val finalTitle = extracted.title.ifEmpty { appLabel }
        val finalBody = extracted.body.ifEmpty {
            if (extracted.hasMedia) "Media attachment" else finalTitle
        }

        val filterDecision = CommunicationMessageFilter.evaluateIncoming(
            sbn = sbn,
            extracted = extracted,
            resolvedTitle = finalTitle,
            resolvedBody = finalBody,
            appLabel = appLabel
        )
        if (!filterDecision.isCommunication) {
            if (!filterDecision.isSilentOngoing) {
                repository.recordCaptureDiagnostic(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    titlePreview = finalTitle,
                    outcome = CaptureOutcome.SKIPPED_SYSTEM_MESSAGE,
                    detail = filterDecision.reason ?: "Skipped non-communication system/status notification."
                )
            }
            return
        }

        val sessionOpenedAt = repository.sessionPreferences.ensureSessionOpenedAt()
        if (sbn.postTime < sessionOpenedAt) {
            if (!isCatchUp) {
                repository.recordCaptureDiagnostic(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    titlePreview = finalTitle,
                    outcome = CaptureOutcome.SKIPPED_PRE_SESSION,
                    detail = "Skipped alert posted before the active session start time."
                )
            }
            return
        }

        repository.sessionPreferences.recordObservedApp(sbn.packageName, appLabel)

        // Leave opted-out apps completely untouched so their original notifications remain in the shade
        if (!repository.isAppOptedIn(sbn.packageName)) {
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = finalTitle,
                outcome = CaptureOutcome.SKIPPED_OPTED_OUT,
                detail = "App is opted out of Nepho interception; left original notification in shade."
            )
            return
        }

        val shadeMode = repository.sessionPreferences.getEffectiveShadeMode(sbn.packageName)

        // Blocked apps: cancel the original notification and capture nothing
        if (shadeMode == ShadeMode.BLOCKED) {
            selfCanceledPostTimes[sbn.key] = sbn.postTime
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
                // Listener may have disconnected concurrently
            }
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = finalTitle,
                outcome = CaptureOutcome.SKIPPED_BLOCKED,
                detail = "App is set to Blocked; suppressed original notification without capturing."
            )
            return
        }

        // Extract RemoteInput reply handle and secondary action intents BEFORE canceling the original notification
        val actions = notification.actions ?: emptyArray()
        var replyPendingIntent: PendingIntent? = null
        var replyRemoteInputs: Array<android.app.RemoteInput>? = null
        val secondaryActionIntents = LinkedHashMap<String, PendingIntent>()
        val secondaryLabels = mutableListOf<String>()

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty() && action.actionIntent != null && replyPendingIntent == null) {
                replyPendingIntent = action.actionIntent
                replyRemoteInputs = remoteInputs
            } else if (remoteInputs.isNullOrEmpty() && action.actionIntent != null) {
                val label = action.title?.toString()?.trim().orEmpty()
                if (label.isNotEmpty() && !secondaryActionIntents.containsKey(label)) {
                    secondaryActionIntents[label] = action.actionIntent
                    secondaryLabels.add(label)
                }
            }
        }

        val canReply = replyPendingIntent != null && !replyRemoteInputs.isNullOrEmpty()
        val localNotificationId = (sbn.key.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)

        if (canReply || secondaryActionIntents.isNotEmpty()) {
            repository.registerNativeActionHandle(
                key = sbn.key,
                handle = NativeReplyHandle(
                    replyPendingIntent = replyPendingIntent,
                    frameworkRemoteInputs = replyRemoteInputs,
                    secondaryActionIntents = secondaryActionIntents,
                    isSelfBroadcast = false
                )
            )
        }
        repository.registerContentIntent(sbn.key, notification.contentIntent)

        // Persist first; never suppress the original notification if ingestion fails.
        val ingestSucceeded = runCatching {
            repository.ingestNotification(
                key = sbn.key,
                notificationId = localNotificationId,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = finalTitle,
                body = finalBody,
                postTime = sbn.postTime,
                canReply = canReply,
                secondaryActions = secondaryLabels,
                sourceChannel = if (isCatchUp) "LISTENER_CATCHUP" else "LISTENER",
                imageUri = NotificationImageStore.capture(applicationContext, sbn.key, notification)
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to persist notification ${sbn.key}", error)
        }.isSuccess

        if (!ingestSucceeded) {
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = finalTitle,
                outcome = CaptureOutcome.FALLBACK_KEPT_ORIGINAL,
                detail = "Storage error while saving alert; kept original notification in shade."
            )
            return
        }

        // If configured for Nepho Alert without POST_NOTIFICATIONS permission, keep the
        // original shade notification so the user isn't left with zero alert.
        if (shadeMode == ShadeMode.NEPHO_ALERT && !LocalPulseNotifier.hasNotificationPermission(applicationContext)) {
            repository.recordCaptureDiagnostic(
                packageName = sbn.packageName,
                appLabel = appLabel,
                titlePreview = finalTitle,
                outcome = CaptureOutcome.FALLBACK_KEPT_ORIGINAL,
                detail = "Captured to Feed; kept original shade alert because Nepho shade permission is off."
            )
            return
        }

        // Suppress the original app notification so only Nepho surfaces it
        selfCanceledPostTimes[sbn.key] = sbn.postTime
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
            // Listener may have disconnected concurrently
        }

        // Route to Nepho shade only if this app is configured for NEPHO_ALERT
        if (shadeMode == ShadeMode.NEPHO_ALERT) {
            LocalPulseNotifier.postInteractiveLocalNotification(
                context = applicationContext,
                repository = repository,
                key = sbn.key,
                notificationId = localNotificationId,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = finalTitle,
                body = finalBody,
                canReply = canReply,
                secondaryActions = secondaryLabels,
                registerLocalFallbackHandle = false
            )
        }

        val outcome = if (isCatchUp) CaptureOutcome.CAPTURED_CATCHUP else CaptureOutcome.CAPTURED
        val routeText = when (shadeMode) {
            ShadeMode.NEPHO_ALERT -> "Feed + Nepho Alert"
            ShadeMode.SILENT -> "Feed only (Silent)"
            ShadeMode.BLOCKED -> "Blocked"
        }
        val detail = if (isCatchUp) {
            "Recovered during reconnect catch-up • $routeText"
        } else {
            "Captured & original suppressed • $routeText"
        }
        repository.recordCaptureDiagnostic(
            packageName = sbn.packageName,
            appLabel = appLabel,
            titlePreview = finalTitle,
            outcome = outcome,
            detail = detail
        )
    }

    private fun resolveAppLabel(packageName: String): String {
        val supported = SupportedApp.fromPackageName(packageName)
        if (supported != SupportedApp.OTHER) {
            return supported.displayName
        }
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString().ifBlank {
                formatPackageFallback(packageName)
            }
        } catch (_: Exception) {
            formatPackageFallback(packageName)
        }
    }

    private fun formatPackageFallback(packageName: String): String {
        val raw = packageName.substringAfterLast('.').ifBlank { packageName }
        return raw.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    private fun removalReasonLabel(reason: Int): String = when (reason) {
        REASON_CLICK -> "opened"
        REASON_CANCEL -> "swiped away"
        REASON_CANCEL_ALL -> "cleared all"
        REASON_APP_CANCEL -> "cleared by app"
        REASON_APP_CANCEL_ALL -> "cleared all by app"
        REASON_GROUP_SUMMARY_CANCELED -> "group swiped away"
        REASON_SNOOZED -> "snoozed in shade"
        REASON_PACKAGE_BANNED -> "app notifications turned off"
        REASON_CHANNEL_BANNED -> "channel muted"
        else -> "reason $reason"
    }

    companion object {
        private const val TAG = "NephoListener"

        @Volatile
        private var activeInstance: WeakReference<NephoNotificationListener>? = null

        private val _listenerConnected = MutableStateFlow(false)
        val listenerConnectedFlow: StateFlow<Boolean> = _listenerConnected.asStateFlow()

        private val _lastConnectedAt = MutableStateFlow<Long?>(null)
        val lastConnectedAtFlow: StateFlow<Long?> = _lastConnectedAt.asStateFlow()

        private val NEPHO_SHADE_DISMISS_REASONS = setOf(
            REASON_CANCEL,
            REASON_CANCEL_ALL,
            REASON_GROUP_SUMMARY_CANCELED,
            REASON_SNOOZED
        )

        private val SYNCABLE_REMOVAL_REASONS = setOf(
            REASON_CLICK,
            REASON_CANCEL,
            REASON_CANCEL_ALL,
            REASON_APP_CANCEL,
            REASON_APP_CANCEL_ALL,
            REASON_GROUP_SUMMARY_CANCELED,
            REASON_SNOOZED,
            REASON_PACKAGE_BANNED,
            REASON_CHANNEL_BANNED
        )

        fun isListenerEnabled(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun requestRebindService(context: Context): Boolean {
            return runCatching {
                requestRebind(ComponentName(context, NephoNotificationListener::class.java))
                true
            }.onFailure { e ->
                Log.w(TAG, "requestRebind failed", e)
            }.getOrDefault(false)
        }

        /**
         * Two-stage recovery for a stalled ("yellow light") notification listener:
         * cycles the listener service component registration via PackageManager and
         * immediately requests Android's NotificationManagerService to rebind it.
         */
        fun resetAndRebindService(context: Context): Boolean {
            val component = ComponentName(context, NephoNotificationListener::class.java)
            val componentCycled = runCatching {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                true
            }.onFailure { e ->
                Log.w(TAG, "Component toggle during listener reset failed", e)
            }.getOrDefault(false)

            val rebindRequested = requestRebindService(context)
            return componentCycled || rebindRequested
        }

        fun cancelSystemNotification(key: String) {
            try {
                activeInstance?.get()?.cancelNotification(key)
            } catch (_: Exception) {
                // Ignore if listener is not actively bound
            }
        }

        /** Clears every active system notification posted by [packageName] (used when an app is blocked). */
        fun cancelActiveNotificationsForPackage(packageName: String) {
            val listener = activeInstance?.get() ?: return
            try {
                listener.activeNotifications
                    ?.filter { it.packageName == packageName }
                    ?.forEach { listener.cancelNotification(it.key) }
            } catch (_: Exception) {
                // Ignore if listener is not actively bound
            }
        }

        @Suppress("DEPRECATION")
        internal fun extractNotificationContent(
            notification: Notification,
            appLabel: String
        ): ExtractedNotificationContent {
            val extras = notification.extras ?: Bundle.EMPTY
            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?.toString()?.trim().orEmpty()
            val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?.toString()?.trim().orEmpty()
            val normalTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()?.trim().orEmpty()
            val resolvedTitle = conversationTitle.ifEmpty { normalTitle }.ifEmpty { bigTitle }

            // 1. MessagingStyle (Google Messages, WhatsApp, Discord, Slack, GroupMe, etc.)
            val messagingLines = mutableListOf<String>()
            var latestMessagingSender = ""
            var messagingHasMedia = false
            val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!rawMessages.isNullOrEmpty()) {
                for (entry in rawMessages) {
                    val bundle = entry as? Bundle ?: continue
                    val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    val senderName = extractSenderName(bundle)
                    if (bundle.getParcelable<android.net.Uri>("uri") != null) {
                        messagingHasMedia = true
                    }
                    if (msgText.isNotEmpty()) {
                        if (senderName.isNotEmpty()) {
                            latestMessagingSender = senderName
                        }
                        val formatted = if (
                            senderName.isNotEmpty() &&
                            !senderName.equals(resolvedTitle, ignoreCase = true)
                        ) {
                            "$senderName: $msgText"
                        } else {
                            msgText
                        }
                        messagingLines.add(formatted)
                    }
                }
            }
            val messagingBody = messagingLines.takeLast(3).joinToString("\n").trim()

            // 2. BigTextStyle
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()?.trim().orEmpty()

            // 3. InboxStyle (Gmail, Outlook, multi-message summaries)
            val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
                ?.take(5)
                ?.joinToString("\n")
                .orEmpty()

            // 4. Standard text & subtext
            val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                ?.toString()?.trim().orEmpty()

            val directBody = when {
                messagingBody.isNotEmpty() -> messagingBody
                bigText.isNotEmpty() -> bigText
                inboxLines.isNotEmpty() -> inboxLines
                normalText.isNotEmpty() -> normalText
                else -> ""
            }
            val finalTitle = resolvedTitle.ifEmpty { latestMessagingSender }.ifEmpty { appLabel }
            val hasMedia = messagingHasMedia ||
                extras.containsKey(Notification.EXTRA_PICTURE) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    extras.containsKey(Notification.EXTRA_PICTURE_ICON))

            return ExtractedNotificationContent(
                title = if (resolvedTitle.isNotEmpty() || latestMessagingSender.isNotEmpty()) finalTitle else "",
                body = directBody.ifEmpty { subText },
                hasDirectBodyText = directBody.isNotEmpty(),
                hasMedia = hasMedia
            )
        }

        @Suppress("DEPRECATION")
        private fun extractSenderName(messageBundle: Bundle): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val person = messageBundle.getParcelable<Person>("sender_person")
                val personName = person?.name?.toString()?.trim().orEmpty()
                if (personName.isNotEmpty()) return personName
            }
            return messageBundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
        }
    }
}

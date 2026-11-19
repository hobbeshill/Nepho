package com.example.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.data.NativeReplyHandle
import com.example.data.NotificationImageStore
import com.example.data.PulseRepository
import com.example.data.ShadeMode
import com.example.data.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

class NephoNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = WeakReference(this)
        // Strict session start policy: do NOT backfill pre-session notifications on connect.
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val appPkg = applicationContext.packageName
        if (sbn.packageName == appPkg) return

        val notification = sbn.notification ?: return
        // Ignore ongoing/persistent system notifications (foreground services, media players, charging)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        // Ignore group summary wrapper rows so individual messages aren't duplicated
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val repository = PulseRepository.getInstance(applicationContext)
        val extras = notification.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val normalText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val body = bigText?.takeIf { it.isNotEmpty() } ?: normalText.orEmpty()

        val hasMedia = extras?.let {
            it.containsKey(Notification.EXTRA_MESSAGES) ||
                it.containsKey(Notification.EXTRA_PICTURE) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    it.containsKey(Notification.EXTRA_PICTURE_ICON))
        } == true
        if (rawTitle.isEmpty() && body.isEmpty() && !hasMedia) return

        val appLabel = resolveAppLabel(sbn.packageName)
        val finalTitle = rawTitle.ifEmpty { appLabel }
        val finalBody = body.ifEmpty { finalTitle }

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

        serviceScope.launch {
            val sessionOpenedAt = repository.sessionPreferences.ensureSessionOpenedAt()
            if (sbn.postTime < sessionOpenedAt) return@launch

            repository.sessionPreferences.recordObservedApp(sbn.packageName, appLabel)

            // Leave opted-out apps completely untouched so their original notifications remain in the shade
            if (!repository.isAppOptedIn(sbn.packageName)) return@launch

            val shadeMode = repository.sessionPreferences.getEffectiveShadeMode(sbn.packageName)

            // Blocked apps: cancel the original notification and capture nothing
            if (shadeMode == ShadeMode.BLOCKED) {
                try {
                    cancelNotification(sbn.key)
                } catch (_: Exception) {
                    // Listener may have disconnected concurrently
                }
                return@launch
            }

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
                sourceChannel = "LISTENER",
                imageUri = NotificationImageStore.capture(applicationContext, sbn.key, notification)
            )

            // Suppress the original app notification so only Nepho surfaces it
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
                    appLabel = appLabel,
                    title = finalTitle,
                    body = finalBody,
                    canReply = canReply,
                    secondaryActions = secondaryLabels,
                    registerLocalFallbackHandle = false
                )
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null) return
        if (sbn.packageName == applicationContext.packageName) return
        // Only mirror removals initiated by the user or the source app (e.g. the message
        // was read, replied to, or cleared inside the real app). Skip our own
        // cancelNotification() calls and system housekeeping removals.
        if (reason !in SYNCABLE_REMOVAL_REASONS) return

        val repository = PulseRepository.getInstance(applicationContext)
        serviceScope.launch {
            repository.syncDismissalFromSystem(sbn.key)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        serviceScope.cancel()
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

    companion object {
        @Volatile
        private var activeInstance: WeakReference<NephoNotificationListener>? = null

        private val SYNCABLE_REMOVAL_REASONS = setOf(
            REASON_CLICK,
            REASON_CANCEL,
            REASON_CANCEL_ALL,
            REASON_APP_CANCEL,
            REASON_APP_CANCEL_ALL
        )

        fun isListenerEnabled(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
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
    }
}

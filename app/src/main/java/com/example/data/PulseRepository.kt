package com.example.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.local.CaptureDiagnosticEntity
import com.example.data.local.ExtractedLinkEntity
import com.example.data.local.DigestEventEntity
import com.example.data.local.PulseDao
import com.example.data.local.PulseDatabase
import com.example.data.local.PulseNotificationEntity
import com.example.data.local.SessionPreferences
import com.example.notifications.NephoNotificationListener
import com.example.widget.NephoWidgetSnapshot
import com.example.widget.NephoWidgetSnapshotBuilder
import com.example.widget.NephoWidgetUpdater
import com.example.worker.ReminderWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

data class NativeReplyHandle(
    val replyPendingIntent: PendingIntent?,
    val frameworkRemoteInputs: Array<android.app.RemoteInput>? = null,
    val compatRemoteInput: RemoteInput? = null,
    val secondaryActionIntents: Map<String, PendingIntent> = emptyMap(),
    val isSelfBroadcast: Boolean = false
)

class PulseRepository private constructor(
    private val appContext: Context,
    private val dao: PulseDao,
    val sessionPreferences: SessionPreferences
) {
    private val actionHandleMap = ConcurrentHashMap<String, NativeReplyHandle>()
    private val contentIntentMap = ConcurrentHashMap<String, PendingIntent>()
    private val retentionCutoffTicker = MutableStateFlow(computeRetentionCutoff())

    fun registerContentIntent(key: String, intent: PendingIntent?) {
        if (intent != null) contentIntentMap[key] = intent else contentIntentMap.remove(key)
    }

    /** Opens the original conversation, never an unrelated app home screen. */
    fun openSourceConversation(key: String): Boolean {
        val intent = contentIntentMap[key] ?: return false
        return try {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                        } else {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                    )
                }.toBundle()
            } else {
                null
            }
            intent.send(appContext, 0, null, null, null, null, options)
            true
        } catch (e: PendingIntent.CanceledException) {
            contentIntentMap.remove(key, intent)
            Log.w("PulseRepository", "Source conversation action expired", e)
            false
        }
    }

    /**
     * Opens the original conversation and clears the notification from Nepho once acted upon.
     */
    suspend fun openSourceConversationAndClear(key: String): Boolean {
        val opened = openSourceConversation(key)
        if (opened) {
            dismissNotification(key)
        }
        return opened
    }

    fun registerNativeActionHandle(key: String, handle: NativeReplyHandle) {
        actionHandleMap[key] = handle
    }

    fun canSendGifReply(key: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return actionHandleMap[key]?.frameworkRemoteInputs
            ?.any { input -> input.allowedDataTypes.any(::supportsGifMimeType) } == true
    }

    fun shouldDirectShareGif(key: String, packageName: String): Boolean =
        GifReplyStore.shouldDirectShareGif(packageName, canSendGifReply(key))

    fun stageGifAttachment(sourceUri: Uri): CachedGifAttachment? =
        GifReplyStore.cacheGifFromUri(appContext, sourceUri)

    fun refreshRetentionTicker() {
        retentionCutoffTicker.value = computeRetentionCutoff()
    }

    val activeNotificationsFlow: Flow<List<PulseNotificationItem>> =
        dao.observeAllActiveNotifications().map { entities ->
            entities.map { it.toDomain() }
        }

    val activeLinksFlow: Flow<List<ExtractedLinkItem>> =
        dao.observeAllActiveLinks().map { entities ->
            entities.map { it.toDomain() }
        }

    val captureDiagnosticsFlow: Flow<List<CaptureDiagnosticEntity>> =
        dao.observeCaptureDiagnostics(MAX_DIAGNOSTIC_ROWS)

    suspend fun recordCaptureDiagnostic(
        packageName: String,
        appLabel: String,
        titlePreview: String,
        outcome: CaptureOutcome,
        detail: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        dao.recordCaptureDiagnostic(
            entry = CaptureDiagnosticEntity(
                timestamp = timestamp,
                packageName = packageName,
                appLabel = appLabel,
                titlePreview = titlePreview.take(80),
                outcome = outcome.name,
                detail = detail
            ),
            maxRows = MAX_DIAGNOSTIC_ROWS
        )
    }

    suspend fun clearCaptureDiagnostics() {
        dao.clearCaptureDiagnostics()
    }

    suspend fun initializeSession(): Long {
        return sessionPreferences.ensureSessionOpenedAt()
    }

    suspend fun loadWidgetSnapshot(): NephoWidgetSnapshot {
        val sessionOpenedAt = sessionPreferences.ensureSessionOpenedAt()
        val active = dao.getAllActiveNotificationsSnapshot().map { it.toDomain() }
        return NephoWidgetSnapshotBuilder.build(
            activeNotifications = active,
            sessionOpenedAt = sessionOpenedAt,
            isNotificationAccessGranted = NephoNotificationListener.isListenerEnabled(appContext)
        )
    }

    suspend fun purgeExpired(): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - RETENTION_WINDOW_MS
        retentionCutoffTicker.value = cutoff
        val removed = dao.purgeExpiredBefore(cutoff)
        // Clean up stale handles if keys no longer exist
        if (removed > 0) {
            for (key in actionHandleMap.keys + contentIntentMap.keys) {
                if (dao.getByKey(key) == null) {
                    actionHandleMap.remove(key)
                    contentIntentMap.remove(key)
                }
            }
            NotificationImageStore.purgeOrphans(appContext, dao.getAllImageUris().toSet())
            NephoWidgetUpdater.requestUpdate(appContext)
        }
        return removed
    }

    suspend fun ingestNotification(
        key: String,
        notificationId: Int,
        packageName: String,
        appLabel: String,
        title: String,
        body: String,
        postTime: Long,
        canReply: Boolean,
        secondaryActions: List<String>,
        sourceChannel: String,
        imageUri: String? = null
    ): IngestResult {
        val existing = dao.getByKey(key)
        val parsedUrls = UrlExtractor.extractUrls(title, body)
        val urlStrings = parsedUrls.map { it.url }
        val preserveStar = existing?.isStarred == true

        val entity = PulseNotificationEntity(
            key = key,
            notificationId = notificationId,
            packageName = packageName,
            appLabel = appLabel,
            title = title,
            body = body,
            postTime = postTime,
            isRead = false,
            isDismissed = false,
            canReply = canReply,
            secondaryActionsJson = PulseJsonCodec.encodeStringList(secondaryActions),
            repliesJson = existing?.repliesJson ?: "[]",
            urlsJson = PulseJsonCodec.encodeStringList(urlStrings),
            imageUri = imageUri,
            sourceChannel = sourceChannel,
            isStarred = preserveStar,
            reminderAt = existing?.reminderAt,
            reminderNote = existing?.reminderNote
        )

        val linkEntities = parsedUrls.map { parsed ->
            ExtractedLinkEntity(
                id = UrlExtractor.deterministicId(parsed.normalizedUrl),
                url = parsed.url,
                normalizedUrl = parsed.normalizedUrl,
                domain = parsed.domain,
                parentNotificationKey = key,
                packageName = packageName,
                appLabel = appLabel,
                senderTitle = title,
                postTime = postTime,
                isStarred = preserveStar
            )
        }

        dao.insertNotificationAndLinks(entity, linkEntities)
        NephoWidgetUpdater.requestUpdate(appContext)
        return IngestResult(
            extractedLinkCount = linkEntities.size,
            purgedExpiredCount = 0
        )
    }

    suspend fun getNotificationByKey(key: String): PulseNotificationEntity? =
        dao.getByKey(key)

    suspend fun toggleNotificationStar(key: String): Boolean {
        val existing = dao.getByKey(key) ?: return false
        val next = !existing.isStarred
        dao.setNotificationStarred(key, next)
        NephoWidgetUpdater.requestUpdate(appContext)
        return next
    }

    suspend fun toggleLinkStar(linkId: String, currentlyStarred: Boolean): Boolean {
        val next = !currentlyStarred
        dao.setLinkStarred(linkId, next)
        return next
    }

    suspend fun scheduleReminder(key: String, triggerAtMillis: Long, reminderNote: String?) {
        val cleanNote = reminderNote?.trim()?.takeIf { it.isNotEmpty() }
        dao.updateReminder(key, triggerAtMillis, cleanNote)
        ReminderWorker.scheduleReminder(appContext, key, triggerAtMillis, cleanNote)
    }

    suspend fun cancelReminder(key: String) {
        dao.updateReminder(key, null, null)
        ReminderWorker.cancelReminder(appContext, key)
    }

    suspend fun clearReminderMetadataOnly(key: String) {
        dao.updateReminder(key, null, null)
    }

    suspend fun markAsRead(key: String) {
        dao.markAsRead(key)
        NephoWidgetUpdater.requestUpdate(appContext)
    }

    suspend fun isAppOptedIn(packageName: String): Boolean =
        sessionPreferences.isAppOptedIn(packageName)

    suspend fun shouldPostShadeAlert(packageName: String): Boolean =
        sessionPreferences.getEffectiveShadeMode(packageName) == ShadeMode.NEPHO_ALERT

    suspend fun sendInlineReply(
        key: String,
        replyText: String,
        gifUri: Uri? = null,
        fromShadeReceiver: Boolean = false
    ): Boolean = sendReplyWithResult(
        key = key,
        replyText = replyText,
        gifAttachment = null,
        gifUri = gifUri,
        fromShadeReceiver = fromShadeReceiver
    ).success

    suspend fun sendReplyWithResult(
        key: String,
        replyText: String,
        gifAttachment: CachedGifAttachment? = null,
        gifUri: Uri? = null,
        fromShadeReceiver: Boolean = false
    ): ReplyDispatchResult {
        val trimmed = replyText.trim()
        val hasGif = gifAttachment != null || gifUri != null
        if (trimmed.isEmpty() && !hasGif) return ReplyDispatchResult(false)

        val existing = dao.getByKey(key) ?: return ReplyDispatchResult(false)
        val nativeHandle = actionHandleMap[key]

        if (hasGif) {
            val supportsNativeGif = canSendGifReply(key)
            val useDirectShare = GifReplyStore.shouldDirectShareGif(
                packageName = existing.packageName,
                supportsNativeGifReply = supportsNativeGif
            )

            val resolvedAttachment: CachedGifAttachment? = when {
                gifAttachment != null -> gifAttachment
                gifUri != null && gifUri.authority == GifReplyStore.fileProviderAuthority(appContext) ->
                    CachedGifAttachment(filePath = "", shareUri = gifUri)
                gifUri != null -> GifReplyStore.cacheGifFromUri(appContext, gifUri)
                else -> null
            }

            // Native RemoteInput image/gif path: delivers the GIF inside the conversation itself.
            val nativeGifUri = resolvedAttachment?.shareUri ?: gifUri
            val canAttemptNative = !useDirectShare &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nativeGifUri != null &&
                nativeGifUri.scheme == "content" &&
                supportsNativeGif
            if (canAttemptNative && nativeGifUri != null) {
                val shouldDispatchIntent = nativeHandle?.replyPendingIntent != null &&
                    (!fromShadeReceiver || !nativeHandle.isSelfBroadcast)
                var dispatched = false
                if (shouldDispatchIntent && nativeHandle != null) {
                    try {
                        val frameworkInputs = nativeHandle.frameworkRemoteInputs
                        if (!frameworkInputs.isNullOrEmpty()) {
                            runCatching {
                                appContext.grantUriPermission(
                                    existing.packageName,
                                    nativeGifUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                            val fillInIntent = Intent()
                            frameworkInputs
                                .filter { input -> input.allowedDataTypes.any(::supportsGifMimeType) }
                                .forEach { input ->
                                    android.app.RemoteInput.addDataResultToIntent(
                                        input,
                                        fillInIntent,
                                        mapOf(GIF_MIME_TYPE to nativeGifUri)
                                    )
                                }
                            fillInIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val resultsBundle = Bundle().apply {
                                frameworkInputs.forEach { ri ->
                                    if (trimmed.isNotEmpty()) putCharSequence(ri.resultKey, trimmed)
                                }
                            }
                            android.app.RemoteInput.addResultsToIntent(
                                frameworkInputs,
                                fillInIntent,
                                resultsBundle
                            )
                            nativeHandle.replyPendingIntent?.send(appContext, 0, fillInIntent)
                            dispatched = true
                        }
                    } catch (e: PendingIntent.CanceledException) {
                        Log.w("PulseRepository", "Native GIF reply intent expired; falling back to share", e)
                    }
                }
                if (dispatched) {
                    val savedPath = resolvedAttachment?.filePath?.takeIf { it.isNotBlank() }
                    recordReplyAndDismiss(
                        key = key,
                        existing = existing,
                        replyText = trimmed.ifEmpty { "GIF attachment" },
                        gifPath = savedPath
                    )
                    return ReplyDispatchResult(
                        success = true,
                        deliveryMode = GifDeliveryMode.NATIVE_REMOTE_INPUT
                    )
                }
                // Fall through to direct ACTION_SEND share as a last resort.
            }

            val shareUri = resolvedAttachment?.shareUri
                ?: return ReplyDispatchResult(
                    success = false,
                    deliveryMode = GifDeliveryMode.DIRECT_SHARE
                )
            val spec = GifReplyStore.buildDirectShareIntent(
                context = appContext,
                targetPackage = existing.packageName,
                targetAppLabel = existing.appLabel,
                gifUri = shareUri,
                caption = trimmed.ifEmpty { null }
            )
            val launched = try {
                appContext.startActivity(spec.intent)
                true
            } catch (e: ActivityNotFoundException) {
                Log.w("PulseRepository", "No activity available to share GIF for ${existing.packageName}", e)
                false
            } catch (e: SecurityException) {
                Log.w("PulseRepository", "Security exception while sharing GIF for ${existing.packageName}", e)
                false
            }
            if (!launched) {
                return ReplyDispatchResult(
                    success = false,
                    deliveryMode = GifDeliveryMode.DIRECT_SHARE,
                    usedChooserFallback = spec.usedChooserFallback
                )
            }

            val savedPath = resolvedAttachment.filePath.takeIf { it.isNotBlank() }
            recordReplyAndDismiss(
                key = key,
                existing = existing,
                replyText = trimmed.ifEmpty { "GIF attachment" },
                gifPath = savedPath
            )
            return ReplyDispatchResult(
                success = true,
                deliveryMode = GifDeliveryMode.DIRECT_SHARE,
                usedChooserFallback = spec.usedChooserFallback
            )
        }

        // Standard text-only inline reply path.
        val shouldDispatchIntent = nativeHandle?.replyPendingIntent != null &&
            (!fromShadeReceiver || !nativeHandle.isSelfBroadcast)
        if (shouldDispatchIntent && nativeHandle != null) {
            try {
                val fillInIntent = Intent()
                val frameworkInputs = nativeHandle.frameworkRemoteInputs
                val compatInput = nativeHandle.compatRemoteInput
                if (!frameworkInputs.isNullOrEmpty()) {
                    val resultsBundle = Bundle().apply {
                        frameworkInputs.forEach { ri ->
                            if (trimmed.isNotEmpty()) putCharSequence(ri.resultKey, trimmed)
                        }
                    }
                    android.app.RemoteInput.addResultsToIntent(
                        frameworkInputs,
                        fillInIntent,
                        resultsBundle
                    )
                    nativeHandle.replyPendingIntent?.send(appContext, 0, fillInIntent)
                } else if (compatInput != null) {
                    val resultsBundle = Bundle().apply {
                        putCharSequence(compatInput.resultKey, trimmed)
                    }
                    RemoteInput.addResultsToIntent(
                        arrayOf(compatInput),
                        fillInIntent,
                        resultsBundle
                    )
                    nativeHandle.replyPendingIntent?.send(appContext, 0, fillInIntent)
                }
            } catch (_: PendingIntent.CanceledException) {
                // Local intent expired; still record reply in thread
            }
        }

        recordReplyAndDismiss(
            key = key,
            existing = existing,
            replyText = trimmed,
            gifPath = null
        )
        return ReplyDispatchResult(success = true)
    }

    private suspend fun recordReplyAndDismiss(
        key: String,
        existing: PulseNotificationEntity,
        replyText: String,
        gifPath: String?
    ) {
        val currentReplies = PulseJsonCodec.decodeReplies(existing.repliesJson).toMutableList()
        currentReplies.add(
            ReplyEntry(
                text = replyText,
                sentAt = System.currentTimeMillis(),
                gifPath = gifPath
            )
        )
        dao.updateReplies(key, PulseJsonCodec.encodeReplies(currentReplies))
        // Responding completes the alert: clear it from the feed and the shade
        dismissNotification(key)
    }

    suspend fun dismissNotification(key: String) {
        val existing = dao.getByKey(key)
        if (existing != null) {
            NotificationManagerCompat.from(appContext).cancel(existing.notificationId)
        }
        NephoNotificationListener.cancelSystemNotification(key)
        ReminderWorker.cancelReminder(appContext, key)
        actionHandleMap.remove(key)
        contentIntentMap.remove(key)
        dao.dismissByKey(key)
        NephoWidgetUpdater.requestUpdate(appContext)
    }

    /**
     * Dismisses a single notification while keeping in-memory reply and conversation intents
     * alive until the next 24h purge or session reset so tapping Undo restores full functionality.
     */
    suspend fun dismissNotificationWithUndo(key: String): UndoDismissBatch? {
        val existing = dao.getByKey(key) ?: return null
        if (existing.isDismissed) return null
        NotificationManagerCompat.from(appContext).cancel(existing.notificationId)
        NephoNotificationListener.cancelSystemNotification(key)
        ReminderWorker.cancelReminder(appContext, key)
        dao.dismissByKey(key)
        NephoWidgetUpdater.requestUpdate(appContext)
        return UndoDismissBatch(
            items = listOf(
                DismissedItemSnapshot(
                    key = existing.key,
                    reminderAt = existing.reminderAt,
                    reminderNote = existing.reminderNote
                )
            )
        )
    }

    /**
     * Dismisses all active non-starred notifications for [packageName], preserving starred items
     * and returning an [UndoDismissBatch] for one-tap restoration.
     */
    suspend fun dismissUnstarredForPackageWithUndo(packageName: String): UndoDismissBatch {
        val targets = dao.getAllActiveUnstarredByPackage(packageName)
        val keptStarred = dao.countActiveStarredByPackage(packageName)
        val snapshots = targets.map { entity ->
            NotificationManagerCompat.from(appContext).cancel(entity.notificationId)
            NephoNotificationListener.cancelSystemNotification(entity.key)
            ReminderWorker.cancelReminder(appContext, entity.key)
            dao.dismissByKey(entity.key)
            DismissedItemSnapshot(
                key = entity.key,
                reminderAt = entity.reminderAt,
                reminderNote = entity.reminderNote
            )
        }
        if (snapshots.isNotEmpty()) {
            NephoWidgetUpdater.requestUpdate(appContext)
        }
        return UndoDismissBatch(items = snapshots, keptStarredCount = keptStarred)
    }

    suspend fun restoreDismissedBatch(batch: UndoDismissBatch): Int {
        val now = System.currentTimeMillis()
        batch.items.forEach { snapshot ->
            val validReminderAt = snapshot.reminderAt?.takeIf { it > now }
            val validNote = if (validReminderAt != null) snapshot.reminderNote else null
            dao.restoreDismissedByKey(snapshot.key, validReminderAt, validNote)
            if (validReminderAt != null) {
                ReminderWorker.scheduleReminder(appContext, snapshot.key, validReminderAt, validNote)
            }
        }
        if (batch.items.isNotEmpty()) {
            NephoWidgetUpdater.requestUpdate(appContext)
        }
        return batch.items.size
    }

    suspend fun markPackageAsRead(packageName: String): Int {
        val updated = dao.markPackageAsRead(packageName)
        if (updated > 0) {
            NephoWidgetUpdater.requestUpdate(appContext)
        }
        return updated
    }

    suspend fun executeSecondaryAction(
        key: String,
        actionLabel: String,
        fromShadeReceiver: Boolean = false
    ) {
        val handle = actionHandleMap[key]
        val shouldDispatchIntent = handle != null && (!fromShadeReceiver || !handle.isSelfBroadcast)
        val pendingIntent = if (shouldDispatchIntent) {
            handle?.secondaryActionIntents?.get(actionLabel)
        } else {
            null
        }
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
            } catch (_: PendingIntent.CanceledException) {
                // Ignore if cancelled
            }
        }
        // Acting on an alert completes it: clear it from the feed and the shade
        dismissNotification(key)
    }

    /**
     * Mirrors a removal that happened outside Nepho (user or source app cleared the
     * original notification) into the Nepho feed and shade. Ignores stale removals
     * whose [removedPostTime] is older than the currently stored update for [key].
     */
    suspend fun syncDismissalFromSystem(key: String, removedPostTime: Long? = null): Boolean {
        val existing = dao.getByKey(key) ?: return false
        if (existing.isDismissed) return false
        if (removedPostTime != null && existing.postTime > removedPostTime) return false
        dismissNotification(key)
        return true
    }

    suspend fun resetSession(): Long {
        NotificationManagerCompat.from(appContext).cancelAll()
        actionHandleMap.clear()
        contentIntentMap.clear()
        dao.clearUnstarredSessionData()
        NotificationImageStore.purgeOrphans(appContext, dao.getAllImageUris().toSet())
        val now = sessionPreferences.resetSessionOpenedAt()
        retentionCutoffTicker.value = now - RETENTION_WINDOW_MS
        NephoWidgetUpdater.requestUpdate(appContext)
        return now
    }

    private fun computeRetentionCutoff(): Long =
        System.currentTimeMillis() - RETENTION_WINDOW_MS

    companion object {
        const val RETENTION_WINDOW_MS = 86_400_000L
        const val GIF_MIME_TYPE = "image/gif"
        const val MAX_DIAGNOSTIC_ROWS = 100

        @Volatile
        private var INSTANCE: PulseRepository? = null

        fun getInstance(context: Context): PulseRepository {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            if (existing != null && existing.appContext === appCtx) return existing
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && current.appContext === appCtx) {
                    current
                } else {
                    val db = PulseDatabase.getInstance(appCtx)
                    val prefs = SessionPreferences(appCtx)
                    PulseRepository(appCtx, db.pulseDao(), prefs).also {
                        INSTANCE = it
                    }
                }
            }
        }
    }
}

private fun supportsGifMimeType(mimeType: String): Boolean =
    mimeType == PulseRepository.GIF_MIME_TYPE || mimeType == "image/*"

enum class GifDeliveryMode {
    DIRECT_SHARE,
    NATIVE_REMOTE_INPUT
}

data class ReplyDispatchResult(
    val success: Boolean,
    val deliveryMode: GifDeliveryMode? = null,
    val usedChooserFallback: Boolean = false
)

data class IngestResult(
    val extractedLinkCount: Int,
    val purgedExpiredCount: Int
)

enum class CaptureOutcome {
    CAPTURED,
    CAPTURED_CATCHUP,
    SKIPPED_OPTED_OUT,
    SKIPPED_BLOCKED,
    SKIPPED_GROUP_SUMMARY,
    SKIPPED_PRE_SESSION,
    SKIPPED_ONGOING,
    SKIPPED_SYSTEM_MESSAGE,
    SYNCED_REMOVAL,
    SELF_CANCEL_IGNORED,
    FALLBACK_KEPT_ORIGINAL,
    LISTENER_CONNECTED,
    LISTENER_DISCONNECTED,
    LISTENER_RECONNECTING,
    LISTENER_AUTO_RESTARTED
}

data class DismissedItemSnapshot(
    val key: String,
    val reminderAt: Long?,
    val reminderNote: String?
)

data class UndoDismissBatch(
    val items: List<DismissedItemSnapshot>,
    val keptStarredCount: Int = 0
)

private fun PulseNotificationEntity.toDomain(): PulseNotificationItem =
    PulseNotificationItem(
        key = key,
        notificationId = notificationId,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        body = body,
        postTime = postTime,
        isRead = isRead,
        isDismissed = isDismissed,
        canReply = canReply,
        secondaryActions = PulseJsonCodec.decodeStringList(secondaryActionsJson),
        replies = PulseJsonCodec.decodeReplies(repliesJson),
        urls = PulseJsonCodec.decodeStringList(urlsJson),
        imageUri = imageUri,
        sourceChannel = sourceChannel,
        isStarred = isStarred,
        reminderAt = reminderAt,
        reminderNote = reminderNote
    )

private fun ExtractedLinkEntity.toDomain(): ExtractedLinkItem =
    ExtractedLinkItem(
        id = id,
        url = url,
        normalizedUrl = normalizedUrl,
        domain = domain,
        parentNotificationKey = parentNotificationKey,
        packageName = packageName,
        appLabel = appLabel,
        senderTitle = senderTitle,
        postTime = postTime,
        isStarred = isStarred
    )

package com.example.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.local.ExtractedLinkEntity
import com.example.data.local.PulseDao
import com.example.data.local.PulseDatabase
import com.example.data.local.PulseNotificationEntity
import com.example.data.local.SessionPreferences
import com.example.notifications.NephoNotificationListener
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

    fun registerNativeActionHandle(key: String, handle: NativeReplyHandle) {
        actionHandleMap[key] = handle
    }

    fun canSendGifReply(key: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return actionHandleMap[key]?.frameworkRemoteInputs
            ?.any { input -> input.allowedDataTypes.any(::supportsGifMimeType) } == true
    }

    fun refreshRetentionTicker() {
        retentionCutoffTicker.value = computeRetentionCutoff()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotificationsFlow: Flow<List<PulseNotificationItem>> = combine(
        retentionCutoffTicker,
        sessionPreferences.sessionOpenedAtFlow
    ) { rollingCutoff, sessionOpenedAt ->
        maxOf(rollingCutoff, sessionOpenedAt)
    }.distinctUntilChanged().flatMapLatest { effectiveCutoff ->
        dao.observeActiveNotifications(effectiveCutoff).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeLinksFlow: Flow<List<ExtractedLinkItem>> = combine(
        retentionCutoffTicker,
        sessionPreferences.sessionOpenedAtFlow
    ) { rollingCutoff, sessionOpenedAt ->
        maxOf(rollingCutoff, sessionOpenedAt)
    }.distinctUntilChanged().flatMapLatest { effectiveCutoff ->
        dao.observeActiveLinks(effectiveCutoff).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun initializeSession(): Long {
        val openedAt = sessionPreferences.ensureSessionOpenedAt()
        purgeExpired()
        return openedAt
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
        val parsedUrls = UrlExtractor.extractUrls(title, body)
        val urlStrings = parsedUrls.map { it.url }

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
            repliesJson = "[]",
            urlsJson = PulseJsonCodec.encodeStringList(urlStrings),
            imageUri = imageUri,
            sourceChannel = sourceChannel
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
                postTime = postTime
            )
        }

        dao.insertNotificationWithLinks(entity, linkEntities)
        val purgedCount = purgeExpired()
        return IngestResult(
            extractedLinkCount = linkEntities.size,
            purgedExpiredCount = purgedCount
        )
    }

    suspend fun getNotificationByKey(key: String): PulseNotificationEntity? =
        dao.getByKey(key)

    suspend fun toggleNotificationStar(key: String): Boolean {
        val existing = dao.getByKey(key) ?: return false
        val next = !existing.isStarred
        dao.setNotificationStarred(key, next)
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
    ): Boolean {
        val trimmed = replyText.trim()
        if (trimmed.isEmpty() && gifUri == null) return false

        val existing = dao.getByKey(key) ?: return false
        val nativeHandle = actionHandleMap[key]
        if (gifUri != null && (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    gifUri.scheme != "content" ||
                    !canSendGifReply(key)
                )
        ) {
            return false
        }
        val shouldDispatchIntent = nativeHandle?.replyPendingIntent != null &&
            (!fromShadeReceiver || !nativeHandle.isSelfBroadcast)
        var dispatched = false
        if (shouldDispatchIntent && nativeHandle != null) {
            try {
                val fillInIntent = Intent()
                val frameworkInputs = nativeHandle.frameworkRemoteInputs
                val compatInput = nativeHandle.compatRemoteInput
                if (!frameworkInputs.isNullOrEmpty()) {
                    if (gifUri != null) {
                        frameworkInputs
                            .filter { input -> input.allowedDataTypes.any(::supportsGifMimeType) }
                            .forEach { input ->
                                android.app.RemoteInput.addDataResultToIntent(
                                    input,
                                    fillInIntent,
                                    mapOf(GIF_MIME_TYPE to gifUri)
                                )
                            }
                        fillInIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
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
                } else if (compatInput != null) {
                    if (gifUri != null) return false
                    val resultsBundle = Bundle().apply {
                        putCharSequence(compatInput.resultKey, trimmed)
                    }
                    RemoteInput.addResultsToIntent(
                        arrayOf(compatInput),
                        fillInIntent,
                        resultsBundle
                    )
                    nativeHandle.replyPendingIntent?.send(appContext, 0, fillInIntent)
                    dispatched = true
                }
            } catch (_: PendingIntent.CanceledException) {
                if (gifUri != null) return false
                // Local intent expired; still record reply in thread
            }
        }
        if (gifUri != null && !dispatched) return false

        val currentReplies = PulseJsonCodec.decodeReplies(existing.repliesJson).toMutableList()
        currentReplies.add(
            ReplyEntry(
                text = trimmed.ifEmpty { "GIF attachment" },
                sentAt = System.currentTimeMillis()
            )
        )
        dao.updateReplies(key, PulseJsonCodec.encodeReplies(currentReplies))
        // Responding completes the alert: clear it from the feed and the shade
        dismissNotification(key)
        return true
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
     * original notification) into the Nepho feed and shade.
     */
    suspend fun syncDismissalFromSystem(key: String): Boolean {
        val existing = dao.getByKey(key) ?: return false
        if (existing.isDismissed) return false
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
        return now
    }

    private fun computeRetentionCutoff(): Long =
        System.currentTimeMillis() - RETENTION_WINDOW_MS

    companion object {
        const val RETENTION_WINDOW_MS = 86_400_000L
        const val GIF_MIME_TYPE = "image/gif"

        @Volatile
        private var INSTANCE: PulseRepository? = null

        fun getInstance(context: Context): PulseRepository {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = PulseDatabase.getInstance(appCtx)
                val prefs = SessionPreferences(appCtx)
                INSTANCE ?: PulseRepository(appCtx, db.pulseDao(), prefs).also {
                    INSTANCE = it
                }
            }
        }
    }
}

private fun supportsGifMimeType(mimeType: String): Boolean =
    mimeType == PulseRepository.GIF_MIME_TYPE || mimeType == "image/*"

data class IngestResult(
    val extractedLinkCount: Int,
    val purgedExpiredCount: Int
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

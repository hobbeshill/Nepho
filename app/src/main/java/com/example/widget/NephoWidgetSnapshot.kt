package com.example.widget

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp

data class WidgetAppUnreadCount(
    val packageName: String,
    val appLabel: String,
    val shortBadge: String,
    val unreadCount: Int,
    @ColorInt val accentColorArgb: Int,
    val latestPostTime: Long
)

data class WidgetAlertPreview(
    val key: String,
    val threadId: String,
    val packageName: String,
    val appLabel: String,
    val shortBadge: String,
    val title: String,
    val body: String,
    val postTime: Long,
    val isStarred: Boolean,
    @ColorInt val accentColorArgb: Int
)

data class NephoWidgetSnapshot(
    val totalUnread: Int,
    val perAppCounts: List<WidgetAppUnreadCount>,
    val topUnreadAlerts: List<WidgetAlertPreview>,
    val sessionOpenedAt: Long,
    val isNotificationAccessGranted: Boolean,
    val refreshedAt: Long
)

object NephoWidgetSnapshotBuilder {
    const val MAX_ALERT_ROWS = 4

    fun build(
        activeNotifications: List<PulseNotificationItem>,
        sessionOpenedAt: Long,
        isNotificationAccessGranted: Boolean,
        refreshedAt: Long = System.currentTimeMillis()
    ): NephoWidgetSnapshot {
        val unread = activeNotifications.filter { !it.isDismissed && !it.isRead }

        val perAppCounts = unread
            .groupBy { it.packageName }
            .map { (pkg, items) ->
                val newest = items.maxBy { it.postTime }
                val supported = SupportedApp.fromPackageName(pkg)
                WidgetAppUnreadCount(
                    packageName = pkg,
                    appLabel = newest.appLabel.ifBlank { supported.displayName },
                    shortBadge = resolveShortBadge(pkg, newest.appLabel, supported),
                    unreadCount = items.size,
                    accentColorArgb = supported.accentColor.toArgb(),
                    latestPostTime = newest.postTime
                )
            }
            .sortedWith(
                compareByDescending<WidgetAppUnreadCount> { it.unreadCount }
                    .thenByDescending { it.latestPostTime }
            )

        val topUnreadAlerts = unread
            .groupBy { it.threadId }
            .values
            .map { threadGroup ->
                val newest = threadGroup.maxBy { it.postTime }
                val anyStarred = threadGroup.any { it.isStarred }
                val supported = SupportedApp.fromPackageName(newest.packageName)
                val appLabel = newest.appLabel.ifBlank { supported.displayName }
                WidgetAlertPreview(
                    key = newest.key,
                    threadId = newest.threadId,
                    packageName = newest.packageName,
                    appLabel = appLabel,
                    shortBadge = resolveShortBadge(newest.packageName, appLabel, supported),
                    title = newest.title.ifBlank { appLabel },
                    body = newest.body.ifBlank { appLabel },
                    postTime = newest.postTime,
                    isStarred = anyStarred,
                    accentColorArgb = supported.accentColor.toArgb()
                )
            }
            .sortedWith(
                compareByDescending<WidgetAlertPreview> { it.isStarred }
                    .thenByDescending { it.postTime }
            )
            .take(MAX_ALERT_ROWS)

        return NephoWidgetSnapshot(
            totalUnread = unread.size,
            perAppCounts = perAppCounts,
            topUnreadAlerts = topUnreadAlerts,
            sessionOpenedAt = sessionOpenedAt,
            isNotificationAccessGranted = isNotificationAccessGranted,
            refreshedAt = refreshedAt
        )
    }

    internal fun resolveShortBadge(
        packageName: String,
        appLabel: String,
        supported: SupportedApp = SupportedApp.fromPackageName(packageName)
    ): String {
        if (supported != SupportedApp.OTHER || packageName == SupportedApp.OTHER.packageName) {
            return supported.shortBadge
        }
        val clean = appLabel.trim()
        if (clean.isEmpty()) return supported.shortBadge
        val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size >= 2) {
            "${words[0].first()}${words[1].first()}".uppercase()
        } else {
            clean.take(2).uppercase()
        }
    }
}

package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.PulseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NephoUnreadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        refreshWidgetsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        refreshWidgetsAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NephoUnreadWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                refreshWidgetsAsync(context, appWidgetManager, ids)
            }
        }
    }

    private fun refreshWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        workerScope.launch {
            try {
                val snapshot = PulseRepository.getInstance(appContext).loadWidgetSnapshot()
                updateWidgets(appContext, appWidgetManager, appWidgetIds, snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET_THREAD_ID = "com.example.widget.EXTRA_THREAD_ID"
        const val EXTRA_WIDGET_APP_PACKAGE = "com.example.widget.EXTRA_APP_PACKAGE"
        const val EXTRA_WIDGET_APP_LABEL = "com.example.widget.EXTRA_APP_LABEL"
        const val EXTRA_WIDGET_UNREAD_ONLY = "com.example.widget.EXTRA_UNREAD_ONLY"
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"

        private const val REQ_HEADER = 7100
        private const val REQ_REFRESH = 7101
        private const val REQ_EMPTY = 7102
        private const val REQ_APP_PILL_BASE = 7200
        private const val REQ_ROW_BASE = 7300

        private val COLOR_CYAN = Color.parseColor("#22D3EE")
        private val COLOR_EMERALD = Color.parseColor("#34D399")
        private val COLOR_AMBER = Color.parseColor("#FBBF24")
        private val COLOR_TEXT_SECONDARY = Color.parseColor("#94A3B8")

        private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val APP_PILL_IDS = intArrayOf(
            R.id.widget_app_pill_0,
            R.id.widget_app_pill_1,
            R.id.widget_app_pill_2,
            R.id.widget_app_pill_3
        )

        private data class RowViewIds(
            val containerId: Int,
            val badgeId: Int,
            val titleId: Int,
            val bodyId: Int,
            val timeId: Int
        )

        private val ROW_VIEW_IDS = listOf(
            RowViewIds(
                R.id.widget_row_0,
                R.id.widget_row_0_badge,
                R.id.widget_row_0_title,
                R.id.widget_row_0_body,
                R.id.widget_row_0_time
            ),
            RowViewIds(
                R.id.widget_row_1,
                R.id.widget_row_1_badge,
                R.id.widget_row_1_title,
                R.id.widget_row_1_body,
                R.id.widget_row_1_time
            ),
            RowViewIds(
                R.id.widget_row_2,
                R.id.widget_row_2_badge,
                R.id.widget_row_2_title,
                R.id.widget_row_2_body,
                R.id.widget_row_2_time
            ),
            RowViewIds(
                R.id.widget_row_3,
                R.id.widget_row_3_badge,
                R.id.widget_row_3_title,
                R.id.widget_row_3_body,
                R.id.widget_row_3_time
            )
        )

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: NephoWidgetSnapshot
        ) {
            for (widgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
                val views = buildRemoteViews(
                    context = context,
                    snapshot = snapshot,
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        fun resolveMaxRows(minHeightDp: Int): Int = when {
            minHeightDp >= 280 -> 4
            minHeightDp >= 215 -> 3
            minHeightDp >= 145 -> 2
            else -> 1
        }

        fun resolveMaxPills(minWidthDp: Int): Int = when {
            minWidthDp >= 280 -> 4
            minWidthDp >= 210 -> 3
            else -> 2
        }

        fun buildRemoteViews(
            context: Context,
            snapshot: NephoWidgetSnapshot,
            minWidthDp: Int = 250,
            minHeightDp: Int = 150
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_nepho_unread)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Direct Activity PendingIntent for header -> Nepho Feed
            views.setOnClickPendingIntent(
                R.id.widget_header_tap_area,
                createMainActivityPendingIntent(
                    context = context,
                    requestCode = REQ_HEADER
                )
            )

            // Refresh button -> broadcast to NephoUnreadWidgetProvider
            val refreshIntent = Intent(context, NephoUnreadWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                REQ_REFRESH,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            // Status subtitle
            if (!snapshot.isNotificationAccessGranted) {
                views.setTextViewText(
                    R.id.widget_status_subtitle,
                    context.getString(R.string.widget_access_off)
                )
                views.setTextColor(R.id.widget_status_subtitle, COLOR_AMBER)
            } else {
                val updatedLabel = timeFmt.format(Date(snapshot.refreshedAt))
                views.setTextViewText(
                    R.id.widget_status_subtitle,
                    context.getString(R.string.widget_updated_at, updatedLabel)
                )
                views.setTextColor(R.id.widget_status_subtitle, COLOR_TEXT_SECONDARY)
            }

            // Total unread badge pill
            if (snapshot.totalUnread > 0) {
                views.setTextViewText(
                    R.id.widget_unread_count_pill,
                    context.getString(R.string.widget_unread_count, snapshot.totalUnread)
                )
                views.setInt(
                    R.id.widget_unread_count_pill,
                    "setBackgroundResource",
                    R.drawable.widget_pill_cyan
                )
                views.setTextColor(R.id.widget_unread_count_pill, COLOR_CYAN)
            } else {
                views.setTextViewText(
                    R.id.widget_unread_count_pill,
                    context.getString(R.string.widget_all_clear)
                )
                views.setInt(
                    R.id.widget_unread_count_pill,
                    "setBackgroundResource",
                    R.drawable.widget_pill_emerald
                )
                views.setTextColor(R.id.widget_unread_count_pill, COLOR_EMERALD)
            }

            // Per-app unread pills
            val maxPills = resolveMaxPills(minWidthDp)
            val visiblePills = if (snapshot.totalUnread > 0 && minHeightDp >= 110) {
                snapshot.perAppCounts.take(maxPills)
            } else {
                emptyList()
            }
            if (visiblePills.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_app_pills_container, View.VISIBLE)
                APP_PILL_IDS.forEachIndexed { index, pillId ->
                    val app = visiblePills.getOrNull(index)
                    if (app != null) {
                        views.setViewVisibility(pillId, View.VISIBLE)
                        views.setTextViewText(pillId, "${app.shortBadge} ${app.unreadCount}")
                        views.setTextColor(pillId, app.accentColorArgb)
                        views.setOnClickPendingIntent(
                            pillId,
                            createMainActivityPendingIntent(
                                context = context,
                                requestCode = REQ_APP_PILL_BASE + index
                            ) {
                                putExtra(EXTRA_WIDGET_APP_PACKAGE, app.packageName)
                                putExtra(EXTRA_WIDGET_APP_LABEL, app.appLabel)
                                putExtra(EXTRA_WIDGET_UNREAD_ONLY, true)
                            }
                        )
                    } else {
                        views.setViewVisibility(pillId, View.GONE)
                    }
                }
            } else {
                views.setViewVisibility(R.id.widget_app_pills_container, View.GONE)
                APP_PILL_IDS.forEach { views.setViewVisibility(it, View.GONE) }
            }

            // Unread alert rows or empty state
            val maxRows = resolveMaxRows(minHeightDp)
            val visibleAlerts = snapshot.topUnreadAlerts.take(maxRows)
            if (visibleAlerts.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_rows_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                ROW_VIEW_IDS.forEachIndexed { index, rowIds ->
                    val alert = visibleAlerts.getOrNull(index)
                    if (alert != null) {
                        views.setViewVisibility(rowIds.containerId, View.VISIBLE)
                        views.setTextViewText(rowIds.badgeId, alert.shortBadge)
                        views.setTextColor(rowIds.badgeId, alert.accentColorArgb)
                        val titleText = if (alert.isStarred) {
                            context.getString(R.string.widget_starred_prefix, alert.title)
                        } else {
                            alert.title
                        }
                        views.setTextViewText(rowIds.titleId, titleText)
                        views.setTextViewText(rowIds.bodyId, alert.body)
                        views.setTextViewText(
                            rowIds.timeId,
                            formatRelativeTime(alert.postTime, snapshot.refreshedAt)
                        )
                        views.setOnClickPendingIntent(
                            rowIds.containerId,
                            createMainActivityPendingIntent(
                                context = context,
                                requestCode = REQ_ROW_BASE + index
                            ) {
                                putExtra(EXTRA_WIDGET_THREAD_ID, alert.threadId)
                            }
                        )
                    } else {
                        views.setViewVisibility(rowIds.containerId, View.GONE)
                    }
                }
            } else {
                views.setViewVisibility(R.id.widget_rows_container, View.GONE)
                ROW_VIEW_IDS.forEach { views.setViewVisibility(it.containerId, View.GONE) }
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_empty_title,
                    context.getString(R.string.widget_all_caught_up)
                )
                val emptySubtitle = if (!snapshot.isNotificationAccessGranted) {
                    context.getString(R.string.widget_access_off)
                } else {
                    context.getString(R.string.widget_caught_up_clean)
                }
                views.setTextViewText(R.id.widget_empty_subtitle, emptySubtitle)
                views.setOnClickPendingIntent(
                    R.id.widget_empty_container,
                    createMainActivityPendingIntent(
                        context = context,
                        requestCode = REQ_EMPTY
                    )
                )
            }

            return views
        }

        internal fun formatRelativeTime(postTime: Long, now: Long): String {
            val diffMs = (now - postTime).coerceAtLeast(0L)
            val minutes = diffMs / 60_000L
            return when {
                minutes < 1L -> "now"
                minutes < 60L -> "${minutes}m"
                minutes < 24L * 60L -> "${minutes / 60L}h"
                else -> "${minutes / (24L * 60L)}d"
            }
        }

        private fun createMainActivityPendingIntent(
            context: Context,
            requestCode: Int,
            configure: Intent.() -> Unit = {}
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                configure()
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

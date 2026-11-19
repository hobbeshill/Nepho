package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.PulseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object NephoWidgetUpdater {
    private const val TAG = "NephoWidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Triggers an immediate off-main-thread refresh of all placed Nepho home-screen widgets.
     * Short-circuits immediately with zero database work when no widgets are installed.
     */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return
        val provider = ComponentName(appContext, NephoUnreadWidgetProvider::class.java)
        val widgetIds = runCatching {
            appWidgetManager.getAppWidgetIds(provider)
        }.getOrNull() ?: return
        if (widgetIds.isEmpty()) return

        scope.launch {
            runCatching {
                val snapshot = PulseRepository.getInstance(appContext).loadWidgetSnapshot()
                NephoUnreadWidgetProvider.updateWidgets(
                    context = appContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = widgetIds,
                    snapshot = snapshot
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to update Nepho home widgets", error)
            }
        }
    }

    /**
     * Asks the user's launcher to pin the Nepho unread widget onto the home screen (Android 8+).
     * Returns true if the system pinning flow was launched, false if the launcher does not support it.
     */
    fun requestPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(appContext, NephoUnreadWidgetProvider::class.java)
        return runCatching {
            appWidgetManager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
    }
}

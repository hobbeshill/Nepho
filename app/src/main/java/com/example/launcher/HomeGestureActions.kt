package com.example.launcher

import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.R
import kotlin.math.abs

enum class ScreenLockResult {
    LOCKED,
    NEEDS_ADMIN_ACTIVATION,
    UNAVAILABLE
}

object HomeGestureActions {

    private const val TAG = "HomeGestureActions"
    private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val GOOGLE_SEARCH_URL = "https://www.google.com"

    fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, ScreenLockAdminReceiver::class.java)
    }

    fun isScreenLockEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return runCatching { dpm.isAdminActive(adminComponent(context)) }.getOrDefault(false)
    }

    fun turnScreenOff(context: Context): ScreenLockResult {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return ScreenLockResult.UNAVAILABLE
        val component = adminComponent(context)
        val isActive = runCatching { dpm.isAdminActive(component) }.getOrDefault(false)
        if (!isActive) {
            return ScreenLockResult.NEEDS_ADMIN_ACTIVATION
        }
        return try {
            dpm.lockNow()
            ScreenLockResult.LOCKED
        } catch (e: SecurityException) {
            Log.w(TAG, "DevicePolicyManager.lockNow() denied", e)
            ScreenLockResult.NEEDS_ADMIN_ACTIVATION
        } catch (e: Exception) {
            Log.w(TAG, "DevicePolicyManager.lockNow() failed", e)
            ScreenLockResult.UNAVAILABLE
        }
    }

    fun buildActivateScreenLockIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.screen_lock_admin_explanation)
            )
        }
    }

    fun buildGoogleSearchIntents(context: Context): List<Intent> {
        val intents = ArrayList<Intent>(5)

        // 1. Google App global search directly (opens Google Search bar ready for input)
        intents.add(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).apply {
                setPackage(GOOGLE_SEARCH_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        // 2. Google App web search action
        intents.add(
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                setPackage(GOOGLE_SEARCH_PACKAGE)
                putExtra(SearchManager.QUERY, "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        // 3. Direct launch of Google App if installed
        context.packageManager.getLaunchIntentForPackage(GOOGLE_SEARCH_PACKAGE)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            intents.add(launch)
        }

        // 4. Generic global search provider on the device
        intents.add(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        // 5. Universal browser fallback to google.com
        intents.add(
            Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_SEARCH_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        return intents
    }

    fun openGoogleSearch(context: Context): Boolean {
        for (intent in buildGoogleSearchIntents(context)) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Search intent candidate unavailable: ${intent.action} (${intent.`package`})", e)
            }
        }
        return false
    }

    /**
     * Returns true when a gesture represents a deliberate horizontal leftward swipe
     * (finger moved from right to left across the screen) without being dominated by vertical scroll.
     */
    fun isHorizontalSwipeLeft(
        totalDx: Float,
        totalDy: Float,
        thresholdPx: Float
    ): Boolean {
        return totalDx <= -thresholdPx && abs(totalDx) >= abs(totalDy) * 1.5f
    }
}

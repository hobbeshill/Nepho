package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notifications.ListenerRecoveryCoordinator
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager watchdog (15-minute minimum Android interval) that quietly verifies
 * whether Notification Access is granted while the listener is disconnected, and runs silent
 * Stage 1 / Stage 2 rebind recovery in the background without interrupting the user.
 */
class ListenerHealthWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        runCatching {
            ListenerRecoveryCoordinator.runWatchdogCheck(applicationContext)
        }.onFailure { e ->
            Log.w(TAG, "Background listener health check failed", e)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ListenerWatchdog"
        private const val WORK_NAME = "nepho_listener_health_watchdog"
        private const val INTERVAL_MINUTES = 15L

        fun syncSchedule(context: Context, isAccessGranted: Boolean) {
            runCatching {
                val wm = WorkManager.getInstance(context.applicationContext)
                if (isAccessGranted) {
                    val request = PeriodicWorkRequestBuilder<ListenerHealthWatchdogWorker>(
                        INTERVAL_MINUTES,
                        TimeUnit.MINUTES
                    ).build()
                    wm.enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
                } else {
                    wm.cancelUniqueWork(WORK_NAME)
                }
            }.onFailure { e ->
                Log.w(TAG, "Unable to sync listener watchdog WorkManager schedule", e)
            }
        }
    }
}

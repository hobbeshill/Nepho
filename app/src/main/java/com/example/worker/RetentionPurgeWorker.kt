package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.PulseRepository
import java.util.concurrent.TimeUnit

class RetentionPurgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = PulseRepository.getInstance(applicationContext)
            repository.purgeExpired()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pulsehub_24h_retention_purge"

        fun schedulePeriodicPurge(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionPurgeWorker>(
                15,
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

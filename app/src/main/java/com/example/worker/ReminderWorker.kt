package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.PulseRepository
import com.example.notifications.LocalPulseNotifier
import java.util.concurrent.TimeUnit

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(INPUT_KEY) ?: return Result.failure()
        val repository = PulseRepository.getInstance(applicationContext)
        val entity = repository.getNotificationByKey(key) ?: return Result.success()
        if (entity.isDismissed) return Result.success()

        val note = inputData.getString(INPUT_NOTE)?.takeIf { it.isNotBlank() } ?: entity.reminderNote
        LocalPulseNotifier.postReminderNotification(
            context = applicationContext,
            notificationId = entity.notificationId + 50_000,
            appLabel = entity.appLabel,
            title = entity.title,
            body = entity.body,
            reminderNote = note
        )
        repository.clearReminderMetadataOnly(key)
        return Result.success()
    }

    companion object {
        private const val INPUT_KEY = "reminder_notification_key"
        private const val INPUT_NOTE = "reminder_note"

        private fun uniqueWorkName(key: String) = "nepho_reminder_$key"

        fun scheduleReminder(
            context: Context,
            key: String,
            triggerAtMillis: Long,
            reminderNote: String?
        ) {
            val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
            val data = Data.Builder()
                .putString(INPUT_KEY, key)
                .putString(INPUT_NOTE, reminderNote.orEmpty())
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(key),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelReminder(context: Context, key: String) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(key))
        }
    }
}

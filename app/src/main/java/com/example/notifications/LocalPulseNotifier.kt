package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.NativeReplyHandle
import com.example.data.PulseRepository
import com.example.data.pulseThreadId
import com.example.widget.NephoUnreadWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LocalPulseNotifier {
    const val CHANNEL_ID = "nepho_stream"
    const val REMINDER_CHANNEL_ID = "nepho_reminders"
    const val DIGEST_CHANNEL_ID = "nepho_digests"
    const val REMOTE_INPUT_KEY = "pulse_inline_reply"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                }
                manager?.createNotificationChannel(channel)
            }
            if (manager?.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
                val reminderChannel = NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_desc)
                }
                manager?.createNotificationChannel(reminderChannel)
            }
            if (manager?.getNotificationChannel(DIGEST_CHANNEL_ID) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(
                        DIGEST_CHANNEL_ID,
                        context.getString(R.string.digest_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = context.getString(R.string.digest_channel_desc) }
                )
            }
        }
    }

    /** Builds a PendingIntent that opens Nepho directly on the message's thread. */
    private fun buildOpenThreadIntent(
        context: Context,
        notificationId: Int,
        packageName: String,
        title: String
    ): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                NephoUnreadWidgetProvider.EXTRA_WIDGET_THREAD_ID,
                pulseThreadId(packageName, title)
            )
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun postReminderNotification(
        context: Context,
        notificationId: Int,
        packageName: String,
        appLabel: String,
        title: String,
        body: String,
        reminderNote: String?
    ) {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return

        val openAppIntent = buildOpenThreadIntent(
            context = context,
            notificationId = notificationId,
            packageName = packageName,
            title = title
        )

        val displayBody = if (!reminderNote.isNullOrBlank()) {
            "Note: $reminderNote\n$body"
        } else {
            body
        }

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nepho_notification)
            .setContentTitle("Reminder • [$appLabel] $title")
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently; ignore safely
        }
    }

    fun postInteractiveLocalNotification(
        context: Context,
        repository: PulseRepository,
        key: String,
        notificationId: Int,
        packageName: String,
        appLabel: String,
        title: String,
        body: String,
        canReply: Boolean,
        secondaryActions: List<String>,
        registerLocalFallbackHandle: Boolean = true
    ) {
        ensureChannel(context)

        val openAppIntent = buildOpenThreadIntent(
            context = context,
            notificationId = notificationId,
            packageName = packageName,
            title = title
        )

        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(context.getString(R.string.reply_placeholder))
            .build()

        val replyIntent = Intent(context, PulseActionReceiver::class.java).apply {
            action = PulseActionReceiver.ACTION_REPLY
            putExtra(PulseActionReceiver.EXTRA_KEY, key)
            putExtra(PulseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            replyIntent,
            replyFlags
        )

        val secondaryIntents = LinkedHashMap<String, PendingIntent>()
        secondaryActions.take(2).forEachIndexed { idx, label ->
            val secIntent = Intent(context, PulseActionReceiver::class.java).apply {
                action = PulseActionReceiver.ACTION_SECONDARY
                putExtra(PulseActionReceiver.EXTRA_KEY, key)
                putExtra(PulseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(PulseActionReceiver.EXTRA_ACTION_LABEL, label)
            }
            val secPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 2 + idx,
                secIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            secondaryIntents[label] = secPendingIntent
        }

        val dismissIntent = Intent(context, PulseActionReceiver::class.java).apply {
            action = PulseActionReceiver.ACTION_DISMISS
            putExtra(PulseActionReceiver.EXTRA_KEY, key)
            putExtra(PulseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 4,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val starIntent = Intent(context, PulseActionReceiver::class.java).apply {
            action = PulseActionReceiver.ACTION_STAR
            putExtra(PulseActionReceiver.EXTRA_KEY, key)
            putExtra(PulseActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val starPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 5,
            starIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (registerLocalFallbackHandle) {
            // Register real PendingIntent & RemoteInput handles in-memory keyed by notification key
            repository.registerNativeActionHandle(
                key = key,
                handle = NativeReplyHandle(
                    replyPendingIntent = replyPendingIntent,
                    compatRemoteInput = remoteInput,
                    secondaryActionIntents = secondaryIntents,
                    isSelfBroadcast = true
                )
            )
        }

        if (!hasNotificationPermission(context)) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nepho_notification)
            .setContentTitle("[$appLabel] $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Mirror the actions Nepho offers in-app: Reply, the source app's own actions,
        // Dismiss, and Star. The shade renders at most 3 action buttons, so prioritize
        // Reply and the app's native actions, then fill with Dismiss/Star.
        var actionSlots = 3
        if (canReply) {
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.action_send),
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
            builder.addAction(replyAction)
            actionSlots--
        }

        secondaryIntents.entries.take((actionSlots - 1).coerceAtLeast(0)).forEach { (label, pendingIntent) ->
            builder.addAction(0, label, pendingIntent)
            actionSlots--
        }

        if (actionSlots > 0) {
            builder.addAction(0, context.getString(R.string.action_dismiss), dismissPendingIntent)
            actionSlots--
        }
        if (actionSlots > 0) {
            builder.addAction(0, context.getString(R.string.action_star_message), starPendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently; ignore safely
        }
    }
}

class PulseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()
        val repository = PulseRepository.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val results = RemoteInput.getResultsFromIntent(intent)
                        val replyText = results?.getCharSequence(LocalPulseNotifier.REMOTE_INPUT_KEY)?.toString()
                        if (!replyText.isNullOrBlank()) {
                            repository.sendInlineReply(key, replyText, fromShadeReceiver = true)
                        }
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(context).cancel(notificationId)
                        }
                    }
                    ACTION_SECONDARY -> {
                        val label = intent.getStringExtra(EXTRA_ACTION_LABEL) ?: "Mark Read"
                        repository.executeSecondaryAction(key, label, fromShadeReceiver = true)
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(context).cancel(notificationId)
                        }
                    }
                    ACTION_DISMISS -> {
                        repository.dismissNotification(key)
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(context).cancel(notificationId)
                        }
                    }
                    ACTION_STAR -> {
                        repository.toggleNotificationStar(key)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.example.ACTION_PULSE_REPLY"
        const val ACTION_SECONDARY = "com.example.ACTION_PULSE_SECONDARY"
        const val ACTION_DISMISS = "com.example.ACTION_PULSE_DISMISS"
        const val ACTION_STAR = "com.example.ACTION_PULSE_STAR"
        const val EXTRA_KEY = "extra_pulse_key"
        const val EXTRA_NOTIFICATION_ID = "extra_pulse_notification_id"
        const val EXTRA_ACTION_LABEL = "extra_pulse_action_label"
    }
}

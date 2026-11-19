package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.PulseNotificationItem
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.HighSurface
import com.example.ui.theme.MidnightSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Closeable popup that expands a conversation thread (text threads, X/Twitter threads,
 * email chains) when a feed card is tapped. Shows every captured message in the thread,
 * replies sent from Nepho, a reply box, and the same actions available on the card.
 */
@Composable
fun ThreadPopupDialog(
    threadItems: List<PulseNotificationItem>,
    onDismissRequest: () -> Unit,
    onOpenApp: (PulseNotificationItem) -> Unit,
    onToggleStar: (String) -> Unit,
    onOpenReminderDialog: (PulseNotificationItem) -> Unit,
    canReplyWithGif: Boolean,
    onSendReply: (String, String, Uri?) -> Unit,
    onDismissThread: (List<String>) -> Unit,
    onSecondaryAction: (String, String) -> Unit
) {
    if (threadItems.isEmpty()) return
    val latest = threadItems.last()
    val replyTarget = threadItems.lastOrNull { it.canReply }
    val app = latest.supportedApp
    val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    var replyDraft by rememberSaveable(latest.threadId) { mutableStateOf("") }
    var gifAttachment by rememberSaveable(replyTarget?.key) { mutableStateOf<Uri?>(null) }
    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        gifAttachment = uri
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, app.accentColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("thread_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(app.accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.shortBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = app.accentColor
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenApp(latest) }
                                    .testTag("thread_open_app")
                            ) {
                                Text(
                                    text = latest.appLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = app.accentColor
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.action_open_app, latest.appLabel),
                                    tint = app.accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = latest.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_thread_popup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.thread_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = BorderHairline)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .testTag("thread_message_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = threadItems, key = { it.key }) { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ElevatedSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = timeFormatter.format(Date(message.postTime)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = message.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val mediaSources = buildList {
                                        message.imageUri?.let { add(it) }
                                        addAll(message.imageUrls)
                                    }
                                    mediaSources.forEach { source ->
                                        NotificationMediaImage(
                                            source = source,
                                            contentDescription = "Image from ${message.appLabel}",
                                            modifier = Modifier.fillMaxWidth(),
                                            maxHeight = 260.dp
                                        )
                                    }
                                }
                            }
                            message.replies.forEach { reply ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = HighSurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                                contentDescription = null,
                                                tint = EmeraldOk,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "You: ${reply.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = timeFormatter.format(Date(reply.sentAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldOk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (replyTarget != null) {
                    if (gifAttachment != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.gif_attached),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                            IconButton(
                                onClick = { gifAttachment = null },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("thread_remove_gif_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_remove_gif),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyDraft,
                            onValueChange = { replyDraft = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("thread_reply_input"),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.reply_placeholder),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ElevatedSurface,
                                unfocusedContainerColor = ElevatedSurface,
                                focusedBorderColor = CyanPulse,
                                unfocusedBorderColor = BorderHairline
                            )
                        )
                        if (canReplyWithGif) {
                            IconButton(
                                onClick = { gifPicker.launch("image/gif") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("thread_attach_gif_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.action_attach_gif),
                                    tint = CyanPulse
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (replyDraft.isNotBlank() || gifAttachment != null) {
                                    onSendReply(replyTarget.key, replyDraft, gifAttachment)
                                    replyDraft = ""
                                    gifAttachment = null
                                }
                            },
                            enabled = replyDraft.isNotBlank() || gifAttachment != null,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("thread_send_reply_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.action_send),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_send))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleStar(latest.key) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("thread_star_button")
                    ) {
                        Icon(
                            imageVector = if (latest.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (latest.isStarred) {
                                stringResource(R.string.action_unstar_message)
                            } else {
                                stringResource(R.string.action_star_message)
                            },
                            tint = if (latest.isStarred) AmberSignal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onOpenReminderDialog(latest) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("thread_remind_button")
                    ) {
                        Icon(
                            imageVector = if (latest.reminderAt != null) Icons.Filled.AlarmOn else Icons.Outlined.AlarmAdd,
                            contentDescription = stringResource(R.string.action_set_reminder),
                            tint = if (latest.reminderAt != null) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onDismissThread(threadItems.map { it.key }) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("thread_dismiss_button"),
                        border = BorderStroke(1.dp, BorderHairline),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.thread_dismiss_all),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    latest.secondaryActions.forEach { actionLabel ->
                        TextButton(
                            onClick = { onSecondaryAction(latest.key, actionLabel) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("thread_secondary_action_$actionLabel")
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.CachedGifAttachment
import com.example.data.GifReplyStore
import com.example.data.PulseNotificationItem
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.HighSurface
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Closeable popup that expands a conversation thread (text threads, X/Twitter threads,
 * email chains) when a feed card is tapped. Shows every captured message in the thread,
 * replies sent from Nepho, a keyboard-GIF-capable reply composer, and card actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadPopupDialog(
    threadItems: List<PulseNotificationItem>,
    onDismissRequest: () -> Unit,
    onOpenApp: (PulseNotificationItem) -> Unit,
    onToggleStar: (String) -> Unit,
    onOpenReminderDialog: (PulseNotificationItem) -> Unit,
    shouldDirectShareGif: Boolean,
    onStageGif: suspend (Uri) -> CachedGifAttachment?,
    onGifLoadFailed: () -> Unit = {},
    onSendReply: (String, String, Uri?, CachedGifAttachment?) -> Unit,
    onDismissThread: (List<String>) -> Unit,
    onSecondaryAction: (String, String) -> Unit,
    initialStagedGif: CachedGifAttachment? = null
) {
    if (threadItems.isEmpty()) return
    val latest = threadItems.last()
    val replyTarget = threadItems.lastOrNull { it.canReply }
    val isDirectGifApp = GifReplyStore.isDirectSharePreferredApp(latest.packageName)
    val composerTarget = replyTarget ?: latest.takeIf { isDirectGifApp }
    val app = latest.supportedApp
    val timeFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    var stagedGif by remember(composerTarget?.key, initialStagedGif) { mutableStateOf(initialStagedGif) }
    var isStagingGif by remember(composerTarget?.key) { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

    val currentOnStageGif by rememberUpdatedState(onStageGif)
    val currentOnGifLoadFailed by rememberUpdatedState(onGifLoadFailed)

    fun stageGifUri(uri: Uri) {
        isStagingGif = true
        scope.launch {
            val cached = currentOnStageGif(uri)
            isStagingGif = false
            if (cached != null) {
                stagedGif = cached
            } else {
                currentOnGifLoadFailed()
            }
        }
    }

    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            stageGifUri(uri)
        }
    }

    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            if (transferableContent.hasMediaType(MediaType.Image)) {
                transferableContent.consume { item ->
                    val uri = item.uri
                    if (uri != null) {
                        stageGifUri(uri)
                    }
                    uri != null
                }
            } else {
                transferableContent
            }
        }
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
                        .heightIn(max = 360.dp)
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
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
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
                                        if (!reply.gifPath.isNullOrBlank()) {
                                            NotificationMediaImage(
                                                source = reply.gifPath,
                                                contentDescription = stringResource(R.string.gif_sent_reply_description),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("thread_sent_reply_gif"),
                                                maxHeight = 140.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (composerTarget != null) {
                    key(latest.threadId) {
                        val textFieldState = rememberTextFieldState()
                        val replyDraft = textFieldState.text.toString()
                        val activeGif = stagedGif
                        val canSubmit = !isStagingGif && (
                            activeGif != null || (replyTarget != null && replyDraft.isNotBlank())
                            )
                        val isDirectShareAction = activeGif != null && shouldDirectShareGif

                        if (isStagingGif || activeGif != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ElevatedSurface,
                                border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("thread_gif_preview_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isStagingGif) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = CyanPulse
                                                )
                                                Text(
                                                    text = stringResource(R.string.gif_loading),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CyanPulse
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.GifBox,
                                                    contentDescription = null,
                                                    tint = CyanPulse,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (shouldDirectShareGif) {
                                                        stringResource(R.string.gif_ready_to_share, latest.appLabel)
                                                    } else {
                                                        stringResource(R.string.gif_attached)
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CyanPulse,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (!isStagingGif) {
                                            IconButton(
                                                onClick = { stagedGif = null },
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
                                    if (activeGif != null) {
                                        NotificationMediaImage(
                                            source = activeGif.filePath.ifBlank { activeGif.shareUri.toString() },
                                            contentDescription = stringResource(R.string.gif_preview_description),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("thread_gif_preview_image"),
                                            maxHeight = 140.dp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ElevatedSurface,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isInputFocused) CyanPulse else BorderHairline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        state = textFieldState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .contentReceiver(receiveContentListener)
                                            .onFocusChanged { isInputFocused = it.isFocused }
                                            .testTag("thread_reply_input"),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(CyanPulse),
                                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                                        decorator = { innerTextField ->
                                            if (textFieldState.text.isEmpty()) {
                                                Text(
                                                    text = stringResource(
                                                        if (replyTarget != null) {
                                                            R.string.reply_placeholder
                                                        } else {
                                                            R.string.reply_gif_only_placeholder
                                                        }
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    gifPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("thread_attach_gif_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GifBox,
                                    contentDescription = stringResource(R.string.action_attach_gif),
                                    tint = CyanPulse
                                )
                            }

                            val buttonDescription = if (isDirectShareAction) {
                                stringResource(R.string.action_share_gif_to_app, latest.appLabel)
                            } else {
                                stringResource(R.string.action_send)
                            }
                            Button(
                                onClick = {
                                    if (canSubmit) {
                                        val selectedGif = stagedGif
                                        onSendReply(
                                            composerTarget.key,
                                            replyDraft,
                                            selectedGif?.shareUri,
                                            selectedGif
                                        )
                                        textFieldState.clearText()
                                        stagedGif = null
                                    }
                                },
                                enabled = canSubmit,
                                colors = if (isDirectShareAction) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = CyanPulse,
                                        contentColor = ObsidianBackground
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics { contentDescription = buttonDescription }
                                    .testTag("thread_send_reply_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDirectShareAction) {
                                        Icons.Default.Share
                                    } else {
                                        Icons.AutoMirrored.Filled.Send
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(
                                        if (isDirectShareAction) {
                                            R.string.action_share_gif
                                        } else {
                                            R.string.action_send
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
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

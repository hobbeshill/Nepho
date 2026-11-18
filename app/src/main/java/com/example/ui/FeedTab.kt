package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.HighSurface
import com.example.ui.theme.MidnightSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeedTab(
    notifications: List<PulseNotificationItem>,
    sessionOpenedAt: Long,
    searchQuery: String,
    selectedAppFilter: SupportedApp?,
    unreadOnly: Boolean,
    hasLinksOnly: Boolean,
    bannerDismissed: Boolean,
    hasNotificationPermission: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectAppFilter: (SupportedApp?) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleHasLinksOnly: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSimulateIncoming: () -> Unit,
    onDismissBanner: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onSendReply: (String, String) -> Unit,
    onDismissItem: (String) -> Unit,
    onSecondaryAction: (String, String) -> Unit,
    onOpenVaultTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("feed_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!bannerDismissed) {
            item(key = "onboarding_banner") {
                OnboardingIntakeBanner(
                    hasNotificationPermission = hasNotificationPermission,
                    onEnableLocalAlerts = onRequestNotificationPermission,
                    onSimulateIncoming = onSimulateIncoming,
                    onDismiss = onDismissBanner
                )
            }
        }

        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feed_search_input"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_feed_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search notifications"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("clear_feed_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MidnightSurface,
                        unfocusedContainerColor = MidnightSurface,
                        focusedBorderColor = CyanPulse,
                        unfocusedBorderColor = BorderHairline
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedAppFilter == null,
                        onClick = { onSelectAppFilter(null) },
                        label = { Text(stringResource(R.string.filter_all_apps)) },
                        modifier = Modifier.testTag("filter_chip_all"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulseContainer,
                            selectedLabelColor = CyanPulse
                        )
                    )

                    SupportedApp.filterableApps.forEach { app ->
                        val isSelected = selectedAppFilter == app
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectAppFilter(app) },
                            label = { Text(app.displayName) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(app.accentColor)
                                )
                            },
                            modifier = Modifier.testTag("filter_chip_${app.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = app.accentColor.copy(alpha = 0.20f),
                                selectedLabelColor = app.accentColor
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = unreadOnly,
                        onClick = onToggleUnreadOnly,
                        label = { Text(stringResource(R.string.filter_unread_only)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.MarkEmailUnread,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_unread_only")
                    )

                    FilterChip(
                        selected = hasLinksOnly,
                        onClick = onToggleHasLinksOnly,
                        label = { Text(stringResource(R.string.filter_has_links)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_has_links")
                    )
                }
            }
        }

        if (notifications.isEmpty()) {
            item(key = "empty_feed") {
                EmptyFeedCard(
                    sessionSince = timeFormatter.format(Date(sessionOpenedAt)),
                    onSimulateIncoming = onSimulateIncoming
                )
            }
        } else {
            items(items = notifications, key = { it.key }) { item ->
                NotificationFeedCard(
                    item = item,
                    timeText = timeFormatter.format(Date(item.postTime)),
                    onCardExpand = {
                        if (!item.isRead) onMarkAsRead(item.key)
                    },
                    onSendReply = { replyText -> onSendReply(item.key, replyText) },
                    onDismiss = { onDismissItem(item.key) },
                    onSecondaryAction = { actionLabel -> onSecondaryAction(item.key, actionLabel) },
                    onOpenVaultTab = onOpenVaultTab
                )
            }
        }
    }
}

@Composable
private fun OnboardingIntakeBanner(
    hasNotificationPermission: Boolean,
    onEnableLocalAlerts: () -> Unit,
    onSimulateIncoming: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_banner_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            CyanPulseContainer.copy(alpha = 0.55f),
                            MidnightSurface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.banner_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dismiss_banner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.banner_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onEnableLocalAlerts,
                        enabled = !hasNotificationPermission,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_local_alerts_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            disabledContainerColor = EmeraldOk.copy(alpha = 0.18f),
                            disabledContentColor = EmeraldOk
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasNotificationPermission) {
                                stringResource(R.string.banner_alerts_enabled)
                            } else {
                                stringResource(R.string.banner_enable_alerts)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onSimulateIncoming,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("banner_simulate_button"),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.banner_try_simulate),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFeedCard(
    item: PulseNotificationItem,
    timeText: String,
    onCardExpand: () -> Unit,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit,
    onSecondaryAction: (String) -> Unit,
    onOpenVaultTab: () -> Unit
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(true) }
    var replyDraft by rememberSaveable(item.key) { mutableStateOf("") }
    val app = item.supportedApp
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${item.key}")
            .clickable {
                expanded = !expanded
                onCardExpand()
            },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (!item.isRead) app.accentColor.copy(alpha = 0.45f) else BorderHairline
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isRead) ElevatedSurface else MidnightSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.appLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = app.accentColor
                            )
                            if (!item.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(CyanPulse)
                                )
                            }
                            if (item.isSimulated) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = HighSurface
                                ) {
                                    Text(
                                        text = "SIM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse alert" else "Expand alert",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.urls.isNotEmpty()) {
                AssistChip(
                    onClick = onOpenVaultTab,
                    label = {
                        Text(
                            text = "${item.urls.size} extracted link${if (item.urls.size > 1) "s" else ""} in Vault",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = CyanPulse
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = CyanPulseContainer.copy(alpha = 0.5f),
                        labelColor = CyanPulse
                    ),
                    modifier = Modifier.testTag("card_links_chip_${item.key}")
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.replies.isNotEmpty()) {
                        HorizontalDivider(color = BorderHairline)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.replies.forEach { reply ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = HighSurface
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

                    if (item.canReply) {
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
                                    .testTag("reply_input_${item.key}"),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.reply_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MidnightSurface,
                                    unfocusedContainerColor = MidnightSurface,
                                    focusedBorderColor = CyanPulse,
                                    unfocusedBorderColor = BorderHairline
                                )
                            )

                            Button(
                                onClick = {
                                    if (replyDraft.isNotBlank()) {
                                        onSendReply(replyDraft)
                                        replyDraft = ""
                                    }
                                },
                                enabled = replyDraft.isNotBlank(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("send_reply_button_${item.key}"),
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
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_button_${item.key}"),
                            border = BorderStroke(1.dp, BorderHairline),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_dismiss),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        item.secondaryActions.forEach { actionLabel ->
                            TextButton(
                                onClick = { onSecondaryAction(actionLabel) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("secondary_action_${item.key}_$actionLabel")
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
}

@Composable
private fun EmptyFeedCard(
    sessionSince: String,
    onSimulateIncoming: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_feed_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderHairline),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CyanPulseContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = CyanPulse,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.feed_empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.feed_empty_subtitle, sessionSince),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSimulateIncoming,
                modifier = Modifier.testTag("empty_feed_simulate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_simulate_batch))
            }
        }
    }
}

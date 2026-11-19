package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PulseNotificationItem
import com.example.data.SupportedApp
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSignalContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
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
    starredOnly: Boolean,
    bannerDismissed: Boolean,
    hasNotificationPermission: Boolean,
    isNotificationAccessGranted: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectAppFilter: (SupportedApp?) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleHasLinksOnly: () -> Unit,
    onToggleStarredOnly: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenShadePreferences: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenThread: (PulseNotificationItem) -> Unit,
    onOpenApp: (PulseNotificationItem) -> Unit,
    onOpenSelectedApp: (SupportedApp) -> Unit,
    onToggleStar: (String) -> Unit,
    onSwipeToggleStar: (String) -> Unit,
    onSwipeDismissItem: (String) -> Unit,
    onSwipeStarBlocked: () -> Unit,
    onMarkGroupRead: (packageName: String, appLabel: String) -> Unit,
    onClearGroup: (packageName: String, appLabel: String) -> Unit,
    onOpenReminderDialog: (PulseNotificationItem) -> Unit,
    onDismissItem: (String) -> Unit,
    onOpenVaultTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("feed_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        feedSectionItems(
            notifications = notifications,
            sessionOpenedAt = sessionOpenedAt,
            searchQuery = searchQuery,
            selectedAppFilter = selectedAppFilter,
            unreadOnly = unreadOnly,
            hasLinksOnly = hasLinksOnly,
            starredOnly = starredOnly,
            bannerDismissed = bannerDismissed,
            hasNotificationPermission = hasNotificationPermission,
            isNotificationAccessGranted = isNotificationAccessGranted,
            timeFormatter = timeFormatter,
            collapsedGroups = collapsedGroups,
            onSearchQueryChange = onSearchQueryChange,
            onSelectAppFilter = onSelectAppFilter,
            onToggleUnreadOnly = onToggleUnreadOnly,
            onToggleHasLinksOnly = onToggleHasLinksOnly,
            onToggleStarredOnly = onToggleStarredOnly,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            onOpenShadePreferences = onOpenShadePreferences,
            onDismissBanner = onDismissBanner,
            onOpenThread = onOpenThread,
            onOpenApp = onOpenApp,
            onOpenSelectedApp = onOpenSelectedApp,
            onToggleStar = onToggleStar,
            onSwipeToggleStar = onSwipeToggleStar,
            onSwipeDismissItem = onSwipeDismissItem,
            onSwipeStarBlocked = onSwipeStarBlocked,
            onMarkGroupRead = onMarkGroupRead,
            onClearGroup = onClearGroup,
            onOpenReminderDialog = onOpenReminderDialog,
            onDismissItem = onDismissItem,
            onOpenVaultTab = onOpenVaultTab
        )
    }
}

fun LazyListScope.feedSectionItems(
    notifications: List<PulseNotificationItem>,
    sessionOpenedAt: Long,
    searchQuery: String,
    selectedAppFilter: SupportedApp?,
    unreadOnly: Boolean,
    hasLinksOnly: Boolean,
    starredOnly: Boolean,
    bannerDismissed: Boolean,
    hasNotificationPermission: Boolean,
    isNotificationAccessGranted: Boolean,
    timeFormatter: SimpleDateFormat,
    collapsedGroups: MutableMap<String, Boolean>,
    onSearchQueryChange: (String) -> Unit,
    onSelectAppFilter: (SupportedApp?) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleHasLinksOnly: () -> Unit,
    onToggleStarredOnly: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenShadePreferences: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenThread: (PulseNotificationItem) -> Unit,
    onOpenApp: (PulseNotificationItem) -> Unit,
    onOpenSelectedApp: (SupportedApp) -> Unit,
    onToggleStar: (String) -> Unit,
    onSwipeToggleStar: (String) -> Unit,
    onSwipeDismissItem: (String) -> Unit,
    onSwipeStarBlocked: () -> Unit,
    onMarkGroupRead: (packageName: String, appLabel: String) -> Unit,
    onClearGroup: (packageName: String, appLabel: String) -> Unit,
    onOpenReminderDialog: (PulseNotificationItem) -> Unit,
    onDismissItem: (String) -> Unit,
    onOpenVaultTab: () -> Unit
) {
        if (!isNotificationAccessGranted || !bannerDismissed) {
            item(key = "onboarding_banner") {
                OnboardingIntakeBanner(
                    isNotificationAccessGranted = isNotificationAccessGranted,
                    hasNotificationPermission = hasNotificationPermission,
                    onGrantNotificationAccess = onOpenNotificationAccessSettings,
                    onOpenShadePreferences = onOpenShadePreferences,
                    onEnableLocalAlerts = onRequestNotificationPermission,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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

                    FilterChip(
                        selected = starredOnly,
                        onClick = onToggleStarredOnly,
                        label = { Text(stringResource(R.string.filter_starred_only)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (starredOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = AmberSignal,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_starred_only"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberSignalContainer,
                            selectedLabelColor = AmberSignal
                        )
                    )
                }
            }
        }

        if (selectedAppFilter != null) {
            item(key = "selected_app_link") {
                OutlinedButton(
                    onClick = { onOpenSelectedApp(selectedAppFilter) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_app_link")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open_app, selectedAppFilter.displayName))
                }
            }
        }

        if (notifications.isNotEmpty()) {
            // Group all notifications from the same app together, newest group first
            val appGroups = notifications
                .groupBy { it.packageName }
                .entries
                .sortedByDescending { entry -> entry.value.maxOf { it.postTime } }

            appGroups.forEach { (packageName, groupItems) ->
                val isCollapsed = collapsedGroups[packageName] == true
                val representative = groupItems.first()
                val unreadCount = groupItems.count { !it.isRead }
                val unstarredCount = groupItems.count { !it.isStarred }
                item(key = "app_group_$packageName") {
                    AppGroupHeader(
                        representative = representative,
                        totalCount = groupItems.size,
                        unreadCount = unreadCount,
                        unstarredCount = unstarredCount,
                        collapsed = isCollapsed,
                        onToggleCollapsed = { collapsedGroups[packageName] = !isCollapsed },
                        onOpenApp = { onOpenApp(representative) },
                        onMarkGroupRead = { onMarkGroupRead(packageName, representative.appLabel) },
                        onClearGroup = { onClearGroup(packageName, representative.appLabel) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (!isCollapsed) {
                    items(items = groupItems, key = { it.key }) { item ->
                        SwipeableNotificationCard(
                            item = item,
                            timeText = timeFormatter.format(Date(item.postTime)),
                            onOpenThread = { onOpenThread(item) },
                            onOpenApp = { onOpenApp(item) },
                            onToggleStar = { onToggleStar(item.key) },
                            onSwipeToggleStar = { onSwipeToggleStar(item.key) },
                            onSwipeDismiss = { onSwipeDismissItem(item.key) },
                            onSwipeStarBlocked = onSwipeStarBlocked,
                            onOpenReminderDialog = { onOpenReminderDialog(item) },
                            onDismiss = { onDismissItem(item.key) },
                            onOpenVaultTab = onOpenVaultTab,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
}

@Composable
private fun OnboardingIntakeBanner(
    isNotificationAccessGranted: Boolean,
    hasNotificationPermission: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onOpenShadePreferences: () -> Unit,
    onEnableLocalAlerts: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = if (isNotificationAccessGranted) EmeraldOk else CyanPulse
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_banner_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.16f),
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
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isNotificationAccessGranted) {
                                stringResource(R.string.banner_active_title)
                            } else {
                                stringResource(R.string.banner_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isNotificationAccessGranted) {
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
                }

                Text(
                    text = if (isNotificationAccessGranted) {
                        stringResource(R.string.banner_active_body)
                    } else {
                        stringResource(R.string.banner_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isNotificationAccessGranted) {
                        Button(
                            onClick = onGrantNotificationAccess,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grant_notification_access_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPulse)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.banner_grant_access),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Button(
                            onClick = onOpenShadePreferences,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("banner_shade_prefs_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.banner_shade_prefs),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isNotificationAccessGranted && !hasNotificationPermission) {
                        OutlinedButton(
                            onClick = onEnableLocalAlerts,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("enable_local_alerts_button"),
                            border = BorderStroke(1.dp, BorderHairline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.banner_enable_alerts),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGroupHeader(
    representative: PulseNotificationItem,
    totalCount: Int,
    unreadCount: Int,
    unstarredCount: Int,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onOpenApp: () -> Unit,
    onMarkGroupRead: () -> Unit,
    onClearGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = representative.supportedApp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_group_header_${representative.packageName}")
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleCollapsed() },
        shape = RoundedCornerShape(14.dp),
        color = HighSurface,
        border = BorderStroke(1.dp, app.accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(app.accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.shortBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = app.accentColor
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenApp() }
                            .testTag("open_app_${representative.packageName}")
                    ) {
                        Text(
                            text = representative.appLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = app.accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.action_open_app, representative.appLabel),
                            tint = app.accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (unreadCount > 0) {
                            "$totalCount alert${if (totalCount > 1) "s" else ""} • $unreadCount unread"
                        } else {
                            "$totalCount alert${if (totalCount > 1) "s" else ""}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unreadCount > 0) {
                    IconButton(
                        onClick = onMarkGroupRead,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("group_mark_read_${representative.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = stringResource(R.string.action_group_mark_read, representative.appLabel),
                            tint = CyanPulse,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (unstarredCount > 0) {
                    IconButton(
                        onClick = onClearGroup,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("group_clear_${representative.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.action_group_clear, representative.appLabel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (collapsed) "Expand ${representative.appLabel} group" else "Collapse ${representative.appLabel} group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationCard(
    item: PulseNotificationItem,
    timeText: String,
    onOpenThread: () -> Unit,
    onOpenApp: () -> Unit,
    onToggleStar: () -> Unit,
    onSwipeToggleStar: () -> Unit,
    onSwipeDismiss: () -> Unit,
    onSwipeStarBlocked: () -> Unit,
    onOpenReminderDialog: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVaultTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val currentStarred by rememberUpdatedState(item.isStarred)
    val currentOnSwipeDismiss by rememberUpdatedState(onSwipeDismiss)
    val currentOnSwipeStarBlocked by rememberUpdatedState(onSwipeStarBlocked)
    val currentOnSwipeToggleStar by rememberUpdatedState(onSwipeToggleStar)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            when (targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (currentStarred) {
                        currentOnSwipeStarBlocked()
                        false
                    } else {
                        currentOnSwipeDismiss()
                        true
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnSwipeToggleStar()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("swipe_box_${item.key}"),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSwipeRight = direction == SwipeToDismissBoxValue.StartToEnd
            val isSwipeLeft = direction == SwipeToDismissBoxValue.EndToStart

            val bgColor = when {
                isSwipeRight && item.isStarred -> AmberSignalContainer
                isSwipeRight -> CrimsonErrorContainer
                isSwipeLeft -> AmberSignalContainer
                else -> MidnightSurface
            }
            val borderColor = when {
                isSwipeRight && item.isStarred -> AmberSignal.copy(alpha = 0.45f)
                isSwipeRight -> CrimsonError.copy(alpha = 0.45f)
                isSwipeLeft -> AmberSignal.copy(alpha = 0.45f)
                else -> BorderHairline
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isSwipeLeft) Arrangement.End else Arrangement.Start
                ) {
                    if (isSwipeRight) {
                        val icon = if (item.isStarred) Icons.Outlined.Shield else Icons.Outlined.DeleteSweep
                        val tint = if (item.isStarred) AmberSignal else CrimsonError
                        val label = if (item.isStarred) {
                            stringResource(R.string.swipe_label_protected)
                        } else {
                            stringResource(R.string.swipe_label_dismiss)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = tint
                        )
                    } else if (isSwipeLeft) {
                        val icon = if (item.isStarred) Icons.Outlined.StarBorder else Icons.Filled.Star
                        val label = if (item.isStarred) {
                            stringResource(R.string.swipe_label_unstar)
                        } else {
                            stringResource(R.string.swipe_label_star)
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = AmberSignal
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = AmberSignal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) {
        NotificationFeedCard(
            item = item,
            timeText = timeText,
            onOpenThread = onOpenThread,
            onOpenApp = onOpenApp,
            onToggleStar = onToggleStar,
            onOpenReminderDialog = onOpenReminderDialog,
            onDismiss = onDismiss,
            onOpenVaultTab = onOpenVaultTab
        )
    }
}

@Composable
private fun NotificationFeedCard(
    item: PulseNotificationItem,
    timeText: String,
    onOpenThread: () -> Unit,
    onOpenApp: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenReminderDialog: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVaultTab: () -> Unit
) {
    val app = item.supportedApp
    val reminderFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${item.key}")
            .clickable { onOpenThread() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                item.isStarred -> AmberSignal.copy(alpha = 0.6f)
                !item.isRead -> app.accentColor.copy(alpha = 0.45f)
                else -> BorderHairline
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isRead) ElevatedSurface else MidnightSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.appLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = app.accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenApp() }
                                .testTag("card_open_app_${item.key}")
                        )
                        if (!item.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CyanPulse)
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("star_button_${item.key}")
                    ) {
                        Icon(
                            imageVector = if (item.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (item.isStarred) {
                                stringResource(R.string.action_unstar_message)
                            } else {
                                stringResource(R.string.action_star_message)
                            },
                            tint = if (item.isStarred) AmberSignal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenReminderDialog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remind_button_${item.key}")
                    ) {
                        Icon(
                            imageVector = if (item.reminderAt != null) Icons.Filled.AlarmOn else Icons.Outlined.AlarmAdd,
                            contentDescription = stringResource(R.string.action_set_reminder),
                            tint = if (item.reminderAt != null) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dismiss_button_${item.key}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.isStarred || item.reminderAt != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isStarred) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberSignalContainer,
                            modifier = Modifier.testTag("starred_badge_${item.key}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = AmberSignal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.badge_starred_protected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberSignal
                                )
                            }
                        }
                    }

                    if (item.reminderAt != null) {
                        val notePart = item.reminderNote?.let { " • $it" }.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanPulseContainer,
                            modifier = Modifier
                                .clickable { onOpenReminderDialog() }
                                .testTag("reminder_badge_${item.key}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AlarmOn,
                                    contentDescription = null,
                                    tint = CyanPulse,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Reminder: ${reminderFormatter.format(Date(item.reminderAt))}$notePart",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPulse,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            val mediaPreview = item.imageUri ?: item.imageUrls.firstOrNull()
            if (mediaPreview != null) {
                NotificationMediaImage(
                    source = mediaPreview,
                    contentDescription = "Image from ${item.appLabel}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_media_${item.key}"),
                    maxHeight = 180.dp
                )
            }

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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (item.replies.isNotEmpty()) {
                        stringResource(R.string.card_thread_hint_with_replies, item.replies.size)
                    } else {
                        stringResource(R.string.card_thread_hint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

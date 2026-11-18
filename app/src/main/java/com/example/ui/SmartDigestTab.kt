package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DigestActionItem
import com.example.data.DigestMode
import com.example.data.DigestResult
import com.example.data.SessionMetrics
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
import com.example.ui.theme.IndigoGlow
import com.example.ui.theme.IndigoGlowContainer
import com.example.ui.theme.MidnightSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmartDigestTab(
    metrics: SessionMetrics,
    digestState: DigestUiState,
    onGenerateAiSummary: () -> Unit,
    onGenerateLocalFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("digest_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "metrics_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = stringResource(R.string.digest_metric_total),
                        value = metrics.totalInWindow.toString(),
                        icon = Icons.Default.Notifications,
                        accent = CyanPulse,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.digest_metric_unread),
                        value = metrics.unreadTotal.toString(),
                        icon = Icons.Outlined.MarkEmailUnread,
                        accent = AmberSignal,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.digest_metric_links),
                        value = metrics.totalLinks.toString(),
                        icon = Icons.Default.Link,
                        accent = IndigoGlow,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Unread count breakdown per app
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportedApp.filterableApps.forEach { app ->
                        val count = metrics.unreadByApp[app] ?: 0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MidnightSurface,
                            border = BorderStroke(1.dp, BorderHairline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(app.accentColor)
                                )
                                Text(
                                    text = "${app.displayName}: $count unread",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (count > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "digest_actions") {
            Card(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, IndigoGlow.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = MidnightSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    IndigoGlowContainer.copy(alpha = 0.55f),
                                    MidnightSurface
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = IndigoGlow
                        )
                        Text(
                            text = "24-Hour Smart Digest",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Text(
                        text = "Synthesizes active <24h alerts into app-grouped highlights and prioritized action items using Gemini AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onGenerateAiSummary,
                            enabled = digestState !is DigestUiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_ai_summary_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IndigoGlow,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.digest_generate_ai))
                        }
                    }

                    OutlinedButton(
                        onClick = onGenerateLocalFallback,
                        enabled = digestState !is DigestUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_local_summary_button"),
                        border = BorderStroke(1.dp, BorderHairline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = CyanPulse
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.digest_generate_local))
                    }
                }
            }
        }

        when (digestState) {
            DigestUiState.Idle -> {
                item(key = "digest_idle") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderHairline),
                        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.digest_empty_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.digest_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            DigestUiState.Loading -> {
                item(key = "digest_loading") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, IndigoGlow.copy(alpha = 0.4f)),
                        colors = CardDefaults.cardColors(containerColor = ElevatedSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = IndigoGlow,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Column {
                                Text(
                                    text = "Analyzing ${metrics.totalInWindow} alerts with Gemini...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Extracting schedule updates, billing items, and required replies.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            is DigestUiState.Error -> {
                item(key = "digest_error") {
                    Card(
                        modifier = Modifier.testTag("digest_error_card"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = CrimsonErrorContainer.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = CrimsonError
                                )
                                Text(
                                    text = "AI Summary Unavailable",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CrimsonError
                                )
                            }
                            Text(
                                text = digestState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = onGenerateLocalFallback,
                                modifier = Modifier.testTag("error_fallback_digest_button")
                            ) {
                                Text("Use Offline Rule-Based Digest")
                            }
                        }
                    }
                }
            }

            is DigestUiState.Success -> {
                val result = digestState.result
                item(key = "digest_headline") {
                    DigestHeadlineCard(
                        result = result,
                        generatedTime = timeFmt.format(Date(result.generatedAt))
                    )
                }

                if (result.actionItems.isNotEmpty()) {
                    item(key = "action_items_header") {
                        Text(
                            text = "Action Items (${result.actionItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberSignal
                        )
                    }
                    items(result.actionItems) { action ->
                        ActionItemCard(action)
                    }
                }

                if (result.appGroups.isNotEmpty()) {
                    item(key = "app_groups_header") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "By Application",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanPulse
                        )
                    }
                    items(result.appGroups) { group ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderHairline),
                            colors = CardDefaults.cardColors(containerColor = MidnightSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = group.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyanPulse
                                )
                                group.bulletPoints.forEach { bullet ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = IndigoGlow
                                        )
                                        Text(
                                            text = bullet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderHairline),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DigestHeadlineCard(
    result: DigestResult,
    generatedTime: String
) {
    val isGemini = result.mode == DigestMode.GEMINI_AI
    Card(
        modifier = Modifier.testTag("digest_result_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isGemini) IndigoGlow.copy(alpha = 0.5f) else CyanPulse.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGemini) IndigoGlowContainer else CyanPulseContainer
                ) {
                    Text(
                        text = if (isGemini) "GEMINI AI DIGEST" else "LOCAL RULE DIGEST",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isGemini) IndigoGlow else CyanPulse,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "$generatedTime • ${result.messageCount} msgs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = result.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActionItemCard(action: DigestActionItem) {
    val badgeColor = when (action.priority.lowercase()) {
        "urgent" -> CrimsonError
        "billing" -> AmberSignal
        "schedule" -> EmeraldOk
        else -> CyanPulse
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MidnightSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (action.priority.lowercase()) {
                    "urgent" -> CrimsonErrorContainer
                    "billing" -> AmberSignalContainer
                    else -> HighSurface
                }
            ) {
                Text(
                    text = action.priority.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${action.appName} • ${action.sender}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = action.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

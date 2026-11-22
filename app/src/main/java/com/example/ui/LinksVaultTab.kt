package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExtractedLinkItem
import com.example.ui.theme.LocalCalmVisualPrefs
import com.example.ui.theme.LocalNephoPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LinksVaultTab(
    links: List<ExtractedLinkItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleLinkStar: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchFocusChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("links_vault_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        linksVaultSectionItems(
            links = links,
            searchQuery = searchQuery,
            timeFormatter = timeFormatter,
            context = context,
            onSearchQueryChange = onSearchQueryChange,
            onToggleLinkStar = onToggleLinkStar,
            onSearchFocusChanged = onSearchFocusChanged
        )
    }
}

fun LazyListScope.linksVaultSectionItems(
    links: List<ExtractedLinkItem>,
    searchQuery: String,
    timeFormatter: SimpleDateFormat,
    context: Context,
    onSearchQueryChange: (String) -> Unit,
    onToggleLinkStar: (String, Boolean) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit = {}
) {
        item(key = "vault_header") {
            val palette = LocalNephoPalette.current
            val calmPrefs = LocalCalmVisualPrefs.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (calmPrefs.hideUnreadCounts) {
                            stringResource(R.string.calm_vault_header)
                        } else {
                            "${links.size} Deduplicated URL${if (links.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.accent
                    )
                    if (!calmPrefs.hideUnreadCounts) {
                        val domainCount = links.map { it.domain }.distinct().size
                        Text(
                            text = "$domainCount domain${if (domainCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .keepKeyboardOpenArea("vault_search")
                        .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                        .testTag("vault_search_input"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_vault_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search links vault"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    )
                )
            }
        }

        if (links.isEmpty()) {
            item(key = "empty_vault") {
                val palette = LocalNephoPalette.current
                val emptyShape = RoundedCornerShape(18.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .calmingLitBorder(
                            accentColor = palette.accent,
                            cornerRadius = 18.dp,
                            strokeWidth = 1.15.dp,
                            intensity = CalmBorderIntensity.AMBIENT
                        )
                        .testTag("empty_vault_card"),
                    shape = emptyShape,
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(palette.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.vault_empty_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.vault_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(items = links, key = { it.id }) { link ->
                VaultLinkCard(
                    link = link,
                    timeText = timeFormatter.format(Date(link.postTime)),
                    onToggleStar = { onToggleLinkStar(link.id, link.isStarred) },
                    onOpenBrowser = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onCopyLink = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Nepho Link", link.url))
                    }
                )
            }
        }
}

@Composable
private fun VaultLinkCard(
    link: ExtractedLinkItem,
    timeText: String,
    onToggleStar: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit
) {
    val palette = LocalNephoPalette.current
    val app = link.supportedApp
    val appTint = palette.accentForApp(app.accentColor)
    val cardBorderColor = if (link.isStarred) palette.amber else feedAppColor(link.packageName)
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .calmingLitBorder(
                accentColor = cardBorderColor,
                cornerRadius = 16.dp,
                strokeWidth = if (link.isStarred) 1.3.dp else 1.1.dp,
                intensity = if (link.isStarred) CalmBorderIntensity.ACTIVE else CalmBorderIntensity.AMBIENT
            )
            .testTag("vault_link_card_${link.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (palette.isCalmMode) palette.highSurface else appTint.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = link.appLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = appTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.highSurface
                    ) {
                        Text(
                            text = link.domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (link.isStarred) {
                        Text(
                            text = "Starred",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.amber
                        )
                    }
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_star_button_${link.id}")
                    ) {
                        Icon(
                            imageVector = if (link.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (link.isStarred) "Unstar link" else "Star link",
                            tint = if (link.isStarred) palette.amber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = link.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${link.senderTitle} • $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_browser_button_${link.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentContainer,
                        contentColor = palette.accent
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_open_browser),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onCopyLink,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("copy_link_button_${link.id}"),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_copy_link),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

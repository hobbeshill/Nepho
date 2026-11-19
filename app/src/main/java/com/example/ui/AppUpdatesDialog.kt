package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.CyanPulseContainer
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.EmeraldOkContainer
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.updates.GitHubReleaseUpdater
import com.example.updates.UpdateCheckState

@Composable
fun AppUpdatesDialog(
    savedRepoSlug: String,
    updateState: UpdateCheckState,
    onCheckUpdates: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyReleasesUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var repoInput by rememberSaveable { mutableStateOf(savedRepoSlug) }

    LaunchedEffect(savedRepoSlug) {
        if (repoInput.isBlank() && savedRepoSlug.isNotBlank()) {
            repoInput = savedRepoSlug
        }
    }

    val normalizedSlug = GitHubReleaseUpdater.normalizeRepoSlug(repoInput)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, BorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("app_updates_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = CyanPulse
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.updates_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.updates_installed_version,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPulse
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("updates_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.updates_close)
                        )
                    }
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text(stringResource(R.string.updates_repo_label)) },
                    placeholder = { Text(stringResource(R.string.updates_repo_placeholder)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPulse,
                        unfocusedBorderColor = BorderHairline,
                        focusedLabelColor = CyanPulse
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("updates_repo_input")
                )

                Text(
                    text = stringResource(R.string.updates_repo_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onCheckUpdates(repoInput) },
                        enabled = updateState !is UpdateCheckState.Checking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianBackground
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("updates_check_button")
                    ) {
                        if (updateState is UpdateCheckState.Checking) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = ObsidianBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.updates_action_checking))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.updates_action_check))
                        }
                    }

                    if (normalizedSlug != null) {
                        OutlinedButton(
                            onClick = {
                                onCopyReleasesUrl(GitHubReleaseUpdater.releasesPageUrl(normalizedSlug))
                            },
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("updates_copy_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.updates_action_copy_url),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                when (updateState) {
                    UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit

                    is UpdateCheckState.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = EmeraldOkContainer,
                            border = BorderStroke(1.dp, EmeraldOk.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldOk
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.updates_status_up_to_date,
                                            updateState.latestTag
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldOk
                                    )
                                }
                                TextButton(
                                    onClick = { onOpenUrl(updateState.releasePageUrl) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.updates_action_open_release))
                                }
                            }
                        }
                    }

                    is UpdateCheckState.UpdateAvailable -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CyanPulseContainer,
                            border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.updates_status_available,
                                        updateState.latestTag
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanPulse
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ElevatedSurface,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .heightIn(max = 160.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.updates_notes_header),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanPulse
                                        )
                                        Text(
                                            text = updateState.releaseNotes.ifBlank {
                                                stringResource(R.string.updates_notes_empty)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onOpenUrl(updateState.downloadUrl) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyanPulse,
                                            contentColor = ObsidianBackground
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("updates_download_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.updates_action_download))
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenUrl(updateState.releasePageUrl) },
                                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f)),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(stringResource(R.string.updates_action_open_release))
                                    }
                                }
                            }
                        }
                    }

                    is UpdateCheckState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CrimsonErrorContainer,
                            border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = CrimsonError
                                )
                                Text(
                                    text = updateState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CrimsonError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

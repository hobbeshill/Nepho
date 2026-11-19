package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSignalContainer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CrimsonErrorContainer
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.EmeraldOkContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBackground
import com.example.webbridge.WebBridgeStatus

@Composable
fun WebCompanionDialog(
    status: WebBridgeStatus,
    onToggleBridge: () -> Unit,
    onRotatePin: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onCopyPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                .testTag("web_companion_dialog")
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
                            imageVector = Icons.Outlined.Laptop,
                            contentDescription = null,
                            tint = CyanPulse
                        )
                        Text(
                            text = stringResource(R.string.web_bridge_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("web_bridge_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.web_bridge_close)
                        )
                    }
                }

                // Live status & Start/Stop card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (status.isRunning) EmeraldOkContainer else ElevatedSurface,
                    border = BorderStroke(
                        1.dp,
                        if (status.isRunning) EmeraldOk.copy(alpha = 0.5f) else BorderHairline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (status.isRunning) EmeraldOk else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                            Text(
                                text = stringResource(
                                    if (status.isRunning) R.string.web_bridge_status_running
                                    else R.string.web_bridge_status_stopped
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (status.isRunning) EmeraldOk else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (status.isRunning) {
                            Text(
                                text = stringResource(
                                    R.string.web_bridge_clients_connected,
                                    status.connectedClients
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onToggleBridge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status.isRunning) CrimsonError else CyanPulse,
                                contentColor = ObsidianBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("web_bridge_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (status.isRunning) R.string.web_bridge_action_stop
                                    else R.string.web_bridge_action_start
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (status.lastError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CrimsonErrorContainer,
                        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = status.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonError,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (status.isRunning) {
                    val localUrl = status.localUrl
                    if (localUrl == null || !status.wifiAvailable) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmberSignalContainer,
                            border = BorderStroke(1.dp, AmberSignal.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberSignal
                                )
                                Text(
                                    text = stringResource(R.string.web_bridge_no_wifi_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberSignal
                                )
                            }
                        }
                    }

                    if (localUrl != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ElevatedSurface,
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.web_bridge_url_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = localUrl,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CyanPulse,
                                    modifier = Modifier.testTag("web_bridge_url_text")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onCopyUrl(localUrl) },
                                        border = BorderStroke(1.dp, BorderHairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("web_bridge_copy_url")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.web_bridge_action_copy_url))
                                    }
                                    OutlinedButton(
                                        onClick = { onShareUrl(localUrl) },
                                        border = BorderStroke(1.dp, BorderHairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("web_bridge_share_url")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.web_bridge_action_share_url))
                                    }
                                }
                            }
                        }
                    }

                    // 6-digit Pairing PIN card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ElevatedSurface,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.web_bridge_pin_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = status.formattedPin,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                                color = AmberSignal,
                                modifier = Modifier.testTag("web_bridge_pin_text")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopyPin(status.pairingPin) },
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("web_bridge_copy_pin")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.web_bridge_action_copy_pin))
                                }
                                OutlinedButton(
                                    onClick = onRotatePin,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("web_bridge_rotate_pin")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.web_bridge_action_rotate_pin))
                                }
                            }
                        }
                    }
                }

                // Instructions & LAN privacy card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.web_bridge_how_to_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.web_bridge_how_to_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.web_bridge_privacy_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

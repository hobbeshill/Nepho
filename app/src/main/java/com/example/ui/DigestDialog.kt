package com.example.ui

import android.content.ActivityNotFoundException
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DigestSettingsEntity
import com.example.data.local.DigestSummaryEntity
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

@Composable
fun DigestDialog(
    settings: DigestSettingsEntity?,
    summaries: List<DigestSummaryEntity>,
    onSetEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.digest_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.digest_privacy), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.digest_enable), modifier = Modifier.weight(1f))
                    Switch(checked = settings?.enabled == true, onCheckedChange = onSetEnabled)
                }
                settings?.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (summaries.isEmpty()) Text(stringResource(R.string.digest_empty))
                summaries.forEach { digest ->
                    HorizontalDivider()
                    Text(
                        "${dateFormat.format(Date(digest.startTime))} – ${dateFormat.format(Date(digest.endTime))}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(digest.summary)
                    val urls = JSONArray(digest.urlsJson)
                    if (urls.length() > 0) Text(stringResource(R.string.digest_links))
                    for (i in 0 until urls.length()) {
                        val url = urls.getString(i)
                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(url)
                                } catch (e: ActivityNotFoundException) {
                                    Log.w("DigestDialog", "No browser available", e)
                                    Toast.makeText(context, R.string.digest_link_failed, Toast.LENGTH_SHORT).show()
                                } catch (e: IllegalArgumentException) {
                                    Log.w("DigestDialog", "Could not open digest link", e)
                                    Toast.makeText(context, R.string.digest_link_failed, Toast.LENGTH_SHORT).show()
                                } catch (e: SecurityException) {
                                    Log.w("DigestDialog", "Opening digest link was restricted", e)
                                    Toast.makeText(context, R.string.digest_link_failed, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(0.dp)
                        ) { Text(url) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.digest_close)) } }
    )
}

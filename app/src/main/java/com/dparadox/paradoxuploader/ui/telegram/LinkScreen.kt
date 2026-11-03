package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.ui.util.UiActions

@Composable
fun DownloadLinkScreen(
    onBack: () -> Unit,
    vm: FileActionsViewModel = hiltViewModel()
) {
    val file by vm.file.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val linkUi by vm.linkUi.collectAsStateWithLifecycle()
    val remaining by vm.remainingMs.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.ensureLink() }

    TgScaffold(stringResource(R.string.tg_link_title), onBack = onBack) { padding ->
        val current = link
        when {
            linkUi is LinkUi.Creating && current == null -> CenteredLoading(stringResource(R.string.tg_creating_link))
            current != null -> TgForm(padding) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tg_link_url), style = MaterialTheme.typography.labelMedium)
                        SelectionContainer {
                            Text(current.url, style = MaterialTheme.typography.bodyMedium)
                        }
                        val ms = remaining
                        val expired = ms != null && ms <= 0L
                        Text(
                            if (expired) stringResource(R.string.tg_link_expired)
                            else stringResource(R.string.tg_link_expires_in, formatRemaining(ms ?: (current.expiresAt - System.currentTimeMillis()))),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { UiActions.copy(context, current.url) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.tg_copy_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                    OutlinedButton(onClick = { UiActions.share(context, current.url) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.tg_share_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { UiActions.open(context, current.url) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_open_browser), modifier = Modifier.padding(start = 8.dp))
                }
                Text(stringResource(R.string.tg_link_local_note), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.revoke(); onBack() }) { Text(stringResource(R.string.tg_revoke)) }
            }
            else -> TgForm(padding) {
                // Fallback: URL generation failed -> use the authenticated session directly.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tg_link_failed_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.tg_link_failed_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                (linkUi as? LinkUi.Failed)?.let { ErrorCard(it.error) }
                OutlinedButton(onClick = { vm.ensureLink() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tg_get_link))
                }
                file?.let { f ->
                    DownloadSection(f, download, onStart = vm::startDownload, onCancel = vm::cancelDownload)
                }
            }
        }
    }
}

package com.dparadox.paradoxuploader.ui.telegram

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.ResolvedFile
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.ui.util.UiActions
import com.dparadox.paradoxuploader.ui.util.formatDateTime
import java.io.File

@Composable
fun FileDetailsScreen(
    onBack: () -> Unit,
    onGetLink: () -> Unit,
    vm: FileActionsViewModel = hiltViewModel()
) {
    val file by vm.file.collectAsStateWithLifecycle()
    val linkUi by vm.linkUi.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.linkEvents.collect { (link, action) ->
            when (action) {
                LinkAction.COPY -> UiActions.copy(context, link.url)
                LinkAction.SHARE -> UiActions.share(context, link.url)
            }
        }
    }

    TgScaffold(stringResource(R.string.tg_details_title), onBack = onBack) { padding ->
        val f = file
        TgForm(padding) {
            if (f == null) {
                Text(stringResource(R.string.tg_err_unsupported_media))
                return@TgForm
            }
            FileHeader(f)
            InfoCard(f)

            Button(onClick = onGetLink, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tg_get_link), modifier = Modifier.padding(start = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.ensureLink(LinkAction.COPY) },
                    enabled = linkUi != LinkUi.Creating,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_copy_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
                OutlinedButton(
                    onClick = { vm.ensureLink(LinkAction.SHARE) },
                    enabled = linkUi != LinkUi.Creating,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_share_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
            (linkUi as? LinkUi.Failed)?.let { ErrorCard(it.error) }

            DownloadSection(f, download, onStart = vm::startDownload, onCancel = vm::cancelDownload)
        }
    }
}

@Composable
private fun FileHeader(f: ResolvedFile) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (f.thumbnailPath != null) {
            AsyncImage(
                model = File(f.thumbnailPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp))
            )
        } else {
            Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(f.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 4)
    }
}

@Composable
private fun InfoCard(f: ResolvedFile) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow(R.string.tg_field_name, f.fileName)
            InfoRow(R.string.tg_field_type, kindLabel(f.kind))
            InfoRow(R.string.tg_field_size, Formatters.size(f.sizeBytes))
            InfoRow(R.string.tg_field_mime, f.mimeType)
            InfoRow(R.string.tg_field_chat, f.chatTitle.ifBlank { "—" })
            InfoRow(R.string.tg_field_message, f.messageId.toString())
            InfoRow(R.string.tg_field_date, formatDateTime(f.dateMillis))
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DownloadSection(f: ResolvedFile, state: DownloadUi, onStart: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        DownloadUi.Idle, is DownloadUi.Failed -> {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tg_download_file), modifier = Modifier.padding(start = 8.dp))
            }
            (state as? DownloadUi.Failed)?.let { ErrorCard(it.error) }
        }
        is DownloadUi.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text(
                stringResource(R.string.tg_downloading, Formatters.size(state.bytes), Formatters.size(state.total)),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.tg_cancel)) }
        }
        is DownloadUi.Done -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tg_download_done, state.name), style = MaterialTheme.typography.bodyMedium)
            if (state.uri.startsWith("content://")) {
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(Uri.parse(state.uri), f.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.tg_open_file)) }
            }
        }
    }
}

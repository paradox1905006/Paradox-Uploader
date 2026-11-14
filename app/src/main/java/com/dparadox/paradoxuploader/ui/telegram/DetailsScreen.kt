package com.dparadox.paradoxuploader.ui.telegram

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.ResolvedFile
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.ui.components.DetailField
import com.dparadox.paradoxuploader.ui.components.EmptyState
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.TonePill
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
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    title = stringResource(R.string.tg_details_title),
                    body = stringResource(R.string.tg_err_unsupported_media)
                )
                return@TgForm
            }
            FileHeader(f)

            PrimaryButton(text = stringResource(R.string.tg_get_link), onClick = onGetLink, icon = Icons.Rounded.Link)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { vm.ensureLink(LinkAction.COPY) },
                    enabled = linkUi != LinkUi.Creating,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_copy_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { vm.ensureLink(LinkAction.SHARE) },
                    enabled = linkUi != LinkUi.Creating,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_share_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
            (linkUi as? LinkUi.Failed)?.let { ErrorCard(it.error) }

            DownloadSection(f, download, onStart = vm::startDownload, onCancel = vm::cancelDownload)
            InfoCard(f)
        }
    }
}

@Composable
private fun FileHeader(f: ResolvedFile) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (f.thumbnailPath != null) {
                AsyncImage(
                    model = File(f.thumbnailPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(28.dp))
                )
            } else {
                IconTile(
                    Icons.AutoMirrored.Rounded.InsertDriveFile,
                    size = 96.dp,
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer
                )
            }
            Text(
                f.fileName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TonePill(text = kindLabel(f.kind), container = scheme.secondaryContainer, content = scheme.onSecondaryContainer, dot = false)
                TonePill(text = Formatters.size(f.sizeBytes), container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer, dot = false)
            }
        }
    }
}

@Composable
private fun InfoCard(f: ResolvedFile) {
    ParadoxCard(spacing = Spacing.md) {
        InfoRow(R.string.tg_field_name, f.fileName)
        InfoRow(R.string.tg_field_type, kindLabel(f.kind))
        InfoRow(R.string.tg_field_size, Formatters.size(f.sizeBytes))
        InfoRow(R.string.tg_field_mime, f.mimeType)
        InfoRow(R.string.tg_field_chat, f.chatTitle.ifBlank { "—" })
        InfoRow(R.string.tg_field_message, f.messageId.toString())
        InfoRow(R.string.tg_field_date, formatDateTime(f.dateMillis))
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    DetailField(label = stringResource(label), value = value)
}

@Composable
fun DownloadSection(f: ResolvedFile, state: DownloadUi, onStart: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        DownloadUi.Idle, is DownloadUi.Failed -> {
            OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tg_download_file), modifier = Modifier.padding(start = 8.dp))
            }
            (state as? DownloadUi.Failed)?.let { ErrorCard(it.error) }
        }
        is DownloadUi.Running -> ParadoxCard(spacing = Spacing.sm) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconTile(Icons.Rounded.Download, size = 36.dp, circle = true)
                Text(
                    stringResource(R.string.tg_downloading, Formatters.size(state.bytes), Formatters.size(state.total)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.tg_cancel)) }
            }
            ParadoxProgress(progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f })
        }
        is DownloadUi.Done -> ParadoxCard(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            spacing = Spacing.md
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Text(stringResource(R.string.tg_download_done, state.name), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.uri.startsWith("content://")) {
                Button(onClick = {
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

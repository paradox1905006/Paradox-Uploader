@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.FileType
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.domain.util.LinkExport
import com.dparadox.paradoxuploader.ui.util.QrCode
import com.dparadox.paradoxuploader.ui.util.UiActions
import com.dparadox.paradoxuploader.ui.util.errorText
import com.dparadox.paradoxuploader.ui.util.formatDateTime

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

@Composable
fun StatusChip(status: UploadStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        UploadStatus.UPLOADING -> Triple(R.string.status_uploading, scheme.primaryContainer, scheme.onPrimaryContainer)
        UploadStatus.PROCESSING -> Triple(R.string.status_processing, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        UploadStatus.READY -> Triple(R.string.status_ready, scheme.secondaryContainer, scheme.onSecondaryContainer)
        UploadStatus.DELAYED -> Triple(R.string.status_delayed, scheme.surfaceVariant, scheme.onSurfaceVariant)
        UploadStatus.FAILED -> Triple(R.string.status_failed, scheme.errorContainer, scheme.onErrorContainer)
    }
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

fun FileType.icon(): ImageVector = when (this) {
    FileType.IMAGE -> Icons.Rounded.Image
    FileType.VIDEO -> Icons.Rounded.VideoFile
    FileType.AUDIO -> Icons.Rounded.AudioFile
    FileType.DOCUMENT -> Icons.Rounded.Description
    FileType.ARCHIVE -> Icons.Rounded.FolderZip
    FileType.OTHER -> Icons.Rounded.InsertDriveFile
}

@Composable
fun FileThumb(item: UploadItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val url = item.directUrl
        if (item.fileType == FileType.IMAGE && item.status == UploadStatus.READY && url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = item.fileType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** Copy, Share, Open and QR code buttons for a READY item. */
@Composable
fun LinkActions(item: UploadItem, onQr: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = item.directUrl ?: return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { UiActions.copy(context, url) },
            label = { Text(stringResource(R.string.copy_link)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = { UiActions.share(context, url) },
            label = { Text(stringResource(R.string.share)) },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = { UiActions.open(context, url) },
            label = { Text(stringResource(R.string.open)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        if (onQr != null) {
            AssistChip(
                onClick = onQr,
                label = { Text(stringResource(R.string.qr_code)) },
                leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) { QrCode.bitmap(url).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.qr_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.qr_code_description),
                    modifier = Modifier.size(240.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

data class FileRowActions(
    val onClick: () -> Unit = {},
    val onLongClick: () -> Unit = {},
    val onShowDetails: () -> Unit = {},
    val onCheckNow: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDownload: () -> Unit = {}
)

/** One entry of "My Files". */
@Composable
fun FileRow(
    item: UploadItem,
    selected: Boolean,
    selecting: Boolean,
    actions: FileRowActions,
    modifier: Modifier = Modifier,
    folderName: String? = null
) {
    val context = LocalContext.current
    val isReady = item.status == UploadStatus.READY && item.directUrl != null
    val isPending = item.status == UploadStatus.PROCESSING || item.status == UploadStatus.DELAYED
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = actions.onClick, onLongClick = actions.onLongClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            FileThumb(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.size(item.sizeBytes)} · ${formatDateTime(item.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folderName != null) {
                    Text(
                        text = stringResource(R.string.in_folder, folderName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    isReady -> Text(
                        text = item.directUrl.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    isPending -> Text(
                        text = stringResource(R.string.link_preparing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.status == UploadStatus.FAILED -> Text(
                        text = errorText(item.errorMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.status == UploadStatus.PROCESSING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(item.status)
                    if (isPending) {
                        TextButton(onClick = actions.onCheckNow) { Text(stringResource(R.string.check_now)) }
                    }
                }
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_link)) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            enabled = isReady,
                            onClick = { menuOpen = false; item.directUrl?.let { UiActions.copy(context, it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            enabled = isReady,
                            onClick = { menuOpen = false; item.directUrl?.let { UiActions.share(context, it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                            enabled = isReady,
                            onClick = { menuOpen = false; item.directUrl?.let { UiActions.open(context, it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download)) },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            enabled = isReady,
                            onClick = { menuOpen = false; actions.onDownload() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { menuOpen = false; actions.onShowDetails() }
                        )
                        if (isPending) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.check_now)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; actions.onCheckNow() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; actions.onDelete() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Copy / share the links of [items] as plain text (one link per line) or as CSV.
 * Only files that are READY have a link; the others are skipped.
 */
@Composable
fun LinksMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<UploadItem>,
    folderNames: Map<Long, String>
) {
    val context = LocalContext.current
    val ready = LinkExport.exportable(items)
    val none = ready.isEmpty()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.links_count, ready.size), style = MaterialTheme.typography.labelMedium) },
            enabled = false,
            onClick = {}
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.links_copy_all)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            enabled = !none,
            onClick = {
                onDismiss()
                UiActions.copy(context, LinkExport.text(items), R.string.links_copied)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.links_share_all)) },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            enabled = !none,
            onClick = {
                onDismiss()
                UiActions.share(context, LinkExport.text(items))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.links_copy_csv)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            enabled = !none,
            onClick = {
                onDismiss()
                UiActions.copy(context, LinkExport.csv(items, folderNames), R.string.links_copied)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.links_share_csv)) },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            enabled = !none,
            onClick = {
                onDismiss()
                UiActions.shareFile(
                    context = context,
                    content = LinkExport.csv(items, folderNames),
                    fileName = "paradox-links-${System.currentTimeMillis()}.csv",
                    mimeType = "text/csv"
                )
            }
        )
    }
}

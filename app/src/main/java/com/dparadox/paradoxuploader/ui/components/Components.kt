@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

/** Colour roles for an upload status: container and content. */
@Composable
fun statusColors(status: UploadStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        UploadStatus.UPLOADING -> scheme.primaryContainer to scheme.onPrimaryContainer
        UploadStatus.PROCESSING -> scheme.secondaryContainer to scheme.onSecondaryContainer
        UploadStatus.READY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UploadStatus.DELAYED -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        UploadStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
    }
}

@Composable
fun StatusChip(status: UploadStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        UploadStatus.UPLOADING -> R.string.status_uploading
        UploadStatus.PROCESSING -> R.string.status_processing
        UploadStatus.READY -> R.string.status_ready
        UploadStatus.DELAYED -> R.string.status_delayed
        UploadStatus.FAILED -> R.string.status_failed
    }
    val (container, content) = statusColors(status)
    TonePill(text = stringResource(label), container = container, content = content, modifier = modifier)
}

fun FileType.icon(): ImageVector = when (this) {
    FileType.IMAGE -> Icons.Rounded.Image
    FileType.VIDEO -> Icons.Rounded.VideoFile
    FileType.AUDIO -> Icons.Rounded.AudioFile
    FileType.DOCUMENT -> Icons.Rounded.Description
    FileType.ARCHIVE -> Icons.Rounded.FolderZip
    FileType.OTHER -> Icons.Rounded.InsertDriveFile
}

/** Tonal container colours per file type, so lists are easy to scan. */
@Composable
fun FileType.tileColors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        FileType.IMAGE, FileType.VIDEO -> scheme.primaryContainer to scheme.onPrimaryContainer
        FileType.AUDIO, FileType.ARCHIVE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        FileType.DOCUMENT, FileType.OTHER -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}

@Composable
fun FileThumb(item: UploadItem, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val (container, content) = item.fileType.tileColors()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(container),
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
                tint = content,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** Copy, Share, Open and QR code buttons for a READY item. */
@Composable
fun LinkActions(item: UploadItem, onQr: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = item.directUrl ?: return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilledTonalButton(
            onClick = { UiActions.copy(context, url) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.copy_link))
        }
        OutlinedButton(
            onClick = { UiActions.share(context, url) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.share))
        }
        OutlinedButton(
            onClick = { UiActions.open(context, url) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.open))
        }
        if (onQr != null) {
            OutlinedButton(
                onClick = onQr,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.qr_code))
            }
        }
    }
}

@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) { QrCode.bitmap(url).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.qr_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color.White,
                    modifier = Modifier.size(248.dp)
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.qr_code_description),
                        modifier = Modifier.fillMaxSize().padding(Spacing.md)
                    )
                }
                Spacer(Modifier.size(Spacing.lg))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
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
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "row-container"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = actions.onClick, onLongClick = actions.onLongClick),
        shape = MaterialTheme.shapes.large,
        color = container
    ) {
        Row(
            Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.Top
        ) {
            Box(contentAlignment = Alignment.Center) {
                FileThumb(item)
                if (selecting && selected) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.in_folder, folderName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    ParadoxProgress(progress = null, modifier = Modifier.padding(vertical = 4.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    StatusChip(item.status)
                    if (isPending) {
                        TextButton(
                            onClick = actions.onCheckNow,
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 0.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.check_now))
                        }
                    }
                }
            }
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(Spacing.md))
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = MaterialTheme.shapes.medium
                    ) {
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
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
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

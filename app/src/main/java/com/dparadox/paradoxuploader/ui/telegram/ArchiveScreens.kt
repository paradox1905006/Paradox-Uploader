package com.dparadox.paradoxuploader.ui.telegram

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.domain.telegram.ArchiveChannel
import com.dparadox.paradoxuploader.domain.telegram.ArchiveFile
import com.dparadox.paradoxuploader.domain.telegram.DirectLink
import com.dparadox.paradoxuploader.domain.telegram.FileKind
import com.dparadox.paradoxuploader.ui.components.EmptyState
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InitialAvatar
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.SwitchRow
import com.dparadox.paradoxuploader.ui.components.TonePill
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- Create Cloud Channel

@Composable
fun CreateCloudChannelScreen(
    onCreated: () -> Unit,
    onBack: () -> Unit,
    vm: ArchiveChannelsViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }

    TgScaffold(title = "Create cloud channel", onBack = onBack) { padding ->
        TgForm(padding) {
            TgHeader(
                Icons.Rounded.CloudQueue,
                "The channel is created in your own Telegram account and set as your default archive channel."
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Channel name") },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth()
            )
            ParadoxCard(contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)) {
                SwitchRow(
                    title = "Public channel",
                    subtitle = if (isPublic) "Anyone can find it by username." else "Private (default). Only you can see it.",
                    checked = isPublic,
                    onChecked = { isPublic = it },
                    icon = if (isPublic) Icons.Rounded.Public else Icons.Rounded.Lock
                )
            }
            if (isPublic) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ui.error?.let { ErrorCard(it) }
            PrimaryButton(
                text = "Create channel",
                onClick = { vm.createChannel(title, description, isPublic, username) { onCreated() } },
                enabled = title.isNotBlank(),
                loading = ui.busy,
                icon = Icons.Rounded.Add
            )
        }
    }
}

// ---------------------------------------------------------------- Archive channels

@Composable
fun ArchiveChannelsScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onPasteLink: () -> Unit,
    onUpload: () -> Unit,
    vm: ArchiveChannelsViewModel = hiltViewModel()
) {
    val channels by vm.channels.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    TgScaffold(
        title = "Cloud channels",
        actions = {
            IconButton(onClick = onUpload) { Icon(Icons.Rounded.CloudUpload, contentDescription = "Upload") }
            IconButton(onClick = onPasteLink) { Icon(Icons.Rounded.Link, contentDescription = "Paste link") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (channels.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        icon = Icons.Rounded.CloudQueue,
                        title = "No cloud channels yet",
                        body = "Create a private channel to use as your personal file archive.",
                        action = {
                            Button(onClick = onCreate) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create cloud channel")
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(channels, key = { it.chatId }) { ch ->
                        ChannelCard(
                            channel = ch,
                            onOpen = { onOpen(ch.chatId) },
                            onSetDefault = { vm.setDefault(ch.chatId) },
                            onRemove = { vm.removeReference(ch.chatId) }
                        )
                    }
                }
            }
            if (channels.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Create channel") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
            ui.error?.let {
                Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { ErrorCard(it) }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ArchiveChannel,
    onOpen: () -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    ParadoxCard(onClick = onOpen, contentPadding = PaddingValues(Spacing.lg), spacing = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            InitialAvatar(
                channel.title.ifBlank { "Untitled channel" },
                container = if (channel.isDefault) scheme.tertiaryContainer else scheme.secondaryContainer,
                content = if (channel.isDefault) scheme.onTertiaryContainer else scheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    channel.title.ifBlank { "Untitled channel" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Channel ID: ${channel.chatId}  ·  ${if (channel.isPublic) "Public" else "Private"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (channel.isDefault) {
                TonePill(text = "Default", container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (!channel.isDefault) {
                TextButton(onClick = onSetDefault) { Text("Set default") }
            }
            TextButton(
                onClick = { confirmRemove = true },
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.error)
            ) { Text("Remove") }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
            title = { Text("Remove reference?") },
            text = { Text("This only forgets the channel in Paradox Uploader. The Telegram channel and its files are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- File explorer

@Composable
fun ArchiveExplorerScreen(
    onBack: () -> Unit,
    vm: ArchiveExplorerViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val selectedCount = ui.selected.size
    val selectedBytes = ui.files.filter { it.key in ui.selected }.sumOf { it.sizeBytes }

    TgScaffold(
        title = "Files",
        onBack = onBack,
        actions = {
            TextButton(onClick = { if (ui.selected.isEmpty()) vm.selectAll() else vm.clearSelection() }) {
                Icon(
                    if (ui.selected.isEmpty()) Icons.Rounded.SelectAll else Icons.Rounded.Deselect,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (ui.selected.isEmpty()) "Select all" else "Clear")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = 4.dp)) {
                        TonePill(
                            text = if (ui.loading) "Loading full history… ${ui.scanned} messages scanned, ${ui.files.size} files"
                            else "${ui.files.size} files in ${ui.scanned} messages",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (ui.loading) ParadoxProgress(progress = null)
                    }
                }
                ui.error?.let { err -> item { ErrorCard(err) } }
                if (!ui.loading && ui.files.isEmpty() && ui.error == null) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.FolderOff,
                            title = "Nothing here yet",
                            body = "No downloadable files in this channel yet."
                        )
                    }
                }
                items(ui.files, key = { it.key }) { file ->
                    FileRow(
                        file = file,
                        checked = file.key in ui.selected,
                        onToggle = { vm.toggle(file.key) }
                    )
                }
            }

            if (selectedCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp
                ) {
                    Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            IconTile(Icons.Rounded.Checklist, size = 36.dp, circle = true)
                            Text(
                                "$selectedCount selected · ${formatBytes(selectedBytes)}",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        ui.busy?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ParadoxProgress(progress = null)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(
                                onClick = { vm.downloadSelected() },
                                enabled = ui.busy == null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download")
                            }
                            FilledTonalButton(
                                onClick = { vm.generateLinks() },
                                enabled = ui.busy == null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Get links")
                            }
                        }
                    }
                }
            }
        }
    }

    ui.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.dismissMessage() }) { Text("OK") } }
        )
    }

    if (links.isNotEmpty()) {
        LinksDialog(links = links, onDismiss = { vm.dismissLinks() })
    }
}

@Composable
private fun FileRow(file: ArchiveFile, checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (checked) scheme.secondaryContainer else scheme.surfaceContainerLow,
        label = "tg-file-container"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = file.canSave, onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        color = container
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                kindIcon(file.kind),
                size = 48.dp,
                container = if (checked) scheme.primary else scheme.primaryContainer,
                content = if (checked) scheme.onPrimary else scheme.onPrimaryContainer
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(file.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${kindLabel(file.kind)} · ${formatBytes(file.sizeBytes)} · " +
                        "${DateFormat.getDateInstance().format(Date(file.dateMillis))} · msg ${file.serverMessageId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                if (!file.canSave) {
                    Text("Saving restricted by channel", style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
            Checkbox(checked = checked, onCheckedChange = { if (file.canSave) onToggle() }, enabled = file.canSave)
        }
    }
}

private fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.VIDEO, FileKind.ANIMATION -> Icons.Rounded.VideoFile
    FileKind.AUDIO, FileKind.VOICE -> Icons.Rounded.AudioFile
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.APK -> Icons.Rounded.Android
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@Composable
private fun LinksDialog(links: List<DirectLink>, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val all = links.joinToString("\n") { it.url }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        title = { Text("${links.size} temporary link(s)") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(links, key = { it.token }) { l ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    l.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(l.url)) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, all)
                }
                context.startActivity(Intent.createChooser(send, null))
            }) { Text("Share all") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(all)) }) { Text("Copy all") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

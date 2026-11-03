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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.domain.telegram.ArchiveChannel
import com.dparadox.paradoxuploader.domain.telegram.ArchiveFile
import com.dparadox.paradoxuploader.domain.telegram.DirectLink
import com.dparadox.paradoxuploader.domain.telegram.FileKind
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Channel name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Public channel", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (isPublic) "Anyone can find it by username." else "Private (default). Only you can see it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
            }
            if (isPublic) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ui.error?.let { ErrorCard(it) }
            Button(
                onClick = {
                    vm.createChannel(title, description, isPublic, username) { onCreated() }
                },
                enabled = title.isNotBlank() && !ui.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (ui.busy) InlineSpinner() else Text("Create channel")
            }
            Text(
                "The channel is created in your own Telegram account and set as your default archive channel.",
                style = MaterialTheme.typography.bodySmall
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
            TextButton(onClick = onUpload) { Text("Upload") }
            TextButton(onClick = onPasteLink) { Text("Paste link") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (channels.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No cloud channels yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Create a private channel to use as your personal file archive.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCreate) { Text("Create cloud channel") }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                FloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Icon(Icons.Rounded.Add, contentDescription = "Create channel") }
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
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.title.ifBlank { "Untitled channel" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (channel.isDefault) {
                    Text("Default", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "Channel ID: ${channel.chatId}  ·  ${if (channel.isPublic) "Public" else "Private"}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (!channel.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set default") }
                }
                TextButton(onClick = { confirmRemove = true }) { Text("Remove") }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (ui.loading) "Loading full history… ${ui.scanned} messages scanned, ${ui.files.size} files"
                            else "${ui.files.size} files in ${ui.scanned} messages",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                ui.error?.let { err -> item { ErrorCard(err) } }
                if (!ui.loading && ui.files.isEmpty() && ui.error == null) {
                    item {
                        Text(
                            "No downloadable files in this channel yet.",
                            style = MaterialTheme.typography.bodyMedium
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
                Card(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "$selectedCount selected · ${formatBytes(selectedBytes)}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        ui.busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.downloadSelected() },
                                enabled = ui.busy == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("Download") }
                            OutlinedButton(
                                onClick = { vm.generateLinks() },
                                enabled = ui.busy == null,
                                modifier = Modifier.weight(1f)
                            ) { Text("Get links") }
                        }
                    }
                }
            }
        }
    }

    ui.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
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
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = file.canSave, onClick = onToggle)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { if (file.canSave) onToggle() }, enabled = file.canSave)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    "${kindLabel(file.kind)} · ${formatBytes(file.sizeBytes)} · " +
                        "${DateFormat.getDateInstance().format(Date(file.dateMillis))} · msg ${file.serverMessageId}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!file.canSave) {
                    Text("Saving restricted by channel", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LinksDialog(links: List<DirectLink>, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val all = links.joinToString("\n") { it.url }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${links.size} temporary link(s)") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                items(links, key = { it.token }) { l ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(l.fileName, style = MaterialTheme.typography.labelLarge)
                        Text(l.url, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(l.url)) }) { Text("Copy") }
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

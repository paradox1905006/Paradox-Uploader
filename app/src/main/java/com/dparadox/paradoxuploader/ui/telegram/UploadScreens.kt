package com.dparadox.paradoxuploader.ui.telegram

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.data.telegram.HomeTab
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.ForumTopic
import com.dparadox.paradoxuploader.domain.telegram.PickedFile
import com.dparadox.paradoxuploader.domain.telegram.UploadJob
import com.dparadox.paradoxuploader.domain.telegram.UploadStatus

/** Topic icon colours offered in the create-topic dialog (TDLib's own palette). */
private val TOPIC_COLORS = listOf(7322096, 16766590, 13338331, 9367192, 16749490, 16478047)

// ---------------------------------------------------------------- Destination picker

@Composable
fun UploadDestinationScreen(
    onBack: () -> Unit,
    onPicked: () -> Unit,
    vm: UploadViewModel = hiltViewModel()
) {
    val state by vm.destinations.collectAsStateWithLifecycle()
    var showChannel by remember { mutableStateOf(false) }
    var showGroup by remember { mutableStateOf(false) }

    TgScaffold(
        title = "Choose destination",
        onBack = onBack,
        actions = { TextButton(onClick = { vm.loadDestinations() }) { Text("Refresh") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showChannel = true }, modifier = Modifier.weight(1f)) { Text("New channel") }
                OutlinedButton(onClick = { showGroup = true }, modifier = Modifier.weight(1f)) { Text("New group") }
            }
            state.error?.let { ErrorCard(it, Modifier.padding(horizontal = 16.dp)) }
            if (state.loading) {
                CenteredLoading("Loading your chats…")
            } else if (state.items.isEmpty()) {
                Text(
                    "No channels or groups yet. Create one above.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.chatId }) { dest ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                vm.pickDestination(dest)
                                onPicked()
                            }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(dest.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${dest.typeLabel} · ID ${dest.chatId}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChannel) {
        CreateChannelDialog(
            busy = state.busy,
            onDismiss = { showChannel = false },
            onCreate = { title, desc, isPublic, username ->
                vm.createChannel(title, desc, isPublic, username) {
                    showChannel = false
                    onPicked()
                }
            }
        )
    }
    if (showGroup) {
        CreateGroupDialog(
            busy = state.busy,
            onDismiss = { showGroup = false },
            onCreate = { title, desc, forum ->
                vm.createGroup(title, desc, forum) {
                    showGroup = false
                    onPicked()
                }
            }
        )
    }
    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.dismissDestinationError() },
            text = { Text(tgErrorText(err)) },
            confirmButton = { TextButton(onClick = { vm.dismissDestinationError() }) { Text("OK") } }
        )
    }
}

@Composable
private fun CreateChannelDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, isPublic: Boolean, username: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public channel", modifier = Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                if (isPublic) {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, desc, isPublic, username) }, enabled = title.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
private fun CreateGroupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, forum: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var forum by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Forum group (topics)")
                        Text(if (forum) "Supports topics." else "Basic group, no topics.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = forum, onCheckedChange = { forum = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, desc, forum) }, enabled = title.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Upload dashboard

@Composable
fun UploadDashboardScreen(
    onBack: () -> Unit,
    onChooseDestination: () -> Unit,
    vm: UploadViewModel = hiltViewModel()
) {
    val selection by vm.selection.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val topics by vm.topics.collectAsStateWithLifecycle()
    var caption by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf(true) }
    var showNewTopic by remember { mutableStateOf(false) }

    val dest = selection.destination
    LaunchedEffect(dest?.chatId) {
        if (dest?.supportsTopics == true) vm.loadTopics(dest.chatId)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }

    TgScaffold(title = "Upload to Telegram", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Destination
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dest == null) {
                            Text("No destination selected", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onChooseDestination) { Text("Choose destination") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(dest.title, style = MaterialTheme.typography.titleMedium)
                                    Text(dest.typeLabel, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = onChooseDestination) { Text("Change") }
                            }
                        }
                        if (recent.size > 1) {
                            Text("Recent", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recent, key = { it.chatId }) { r ->
                                    AssistChip(onClick = { vm.pickDestination(r) }, label = { Text(r.title) })
                                }
                            }
                        }
                    }
                }
            }

            // Topics
            if (dest?.supportsTopics == true) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Topic", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (topics.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                TextButton(onClick = { vm.loadTopics(dest.chatId) }) { Text("Refresh") }
                                TextButton(onClick = { showNewTopic = true }) { Text("New topic") }
                            }
                            topics.error?.let { ErrorCard(it) }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selection.topic == null,
                                        onClick = { vm.selectTopic(null) },
                                        label = { Text("General") }
                                    )
                                }
                                items(topics.items, key = { it.threadId }) { t ->
                                    FilterChip(
                                        selected = selection.topic?.threadId == t.threadId,
                                        onClick = { vm.selectTopic(t) },
                                        label = { Text(t.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Files
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Files (${files.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (files.isNotEmpty()) TextButton(onClick = { vm.clearFiles() }) { Text("Clear") }
                        }
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose files") }
                        files.forEach { f -> FileInfoRow(f) { vm.removeFile(f.uri) } }
                    }
                }
            }

            // Caption
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            label = { Text("Caption") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tags (e.g. backup, apk)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Add \"Uploaded via Paradox Uploader\"", modifier = Modifier.weight(1f))
                            Switch(checked = footer, onCheckedChange = { footer = it })
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { vm.startUploads(caption, tags, footer) },
                    enabled = files.isNotEmpty() && dest != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (files.isEmpty()) "Start upload" else "Upload ${files.size} file(s)") }
            }

            // Queue
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.setPaused(!paused) }) { Text(if (paused) "Resume queue" else "Pause queue") }
                    TextButton(onClick = { vm.clearFinished() }) { Text("Clear done") }
                }
            }
            if (queue.isEmpty()) {
                item { Text("Nothing queued yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(queue, key = { it.id }) { job ->
                QueueCard(
                    job = job,
                    onRetry = { vm.retry(job.id) },
                    onCancel = { vm.cancel(job.id) },
                    onOpen = { openLink(context, it) },
                    onShare = { shareLink(context, it) }
                )
            }
        }
    }

    if (showNewTopic) {
        CreateTopicDialog(
            busy = topics.busy,
            onDismiss = { showNewTopic = false },
            onCreate = { name, color ->
                vm.createTopic(name, color)
                showNewTopic = false
            }
        )
    }
}

@Composable
private fun CreateTopicDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(TOPIC_COLORS.first()) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Topic name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Icon colour (optional)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TOPIC_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF000000L or c.toLong()))
                                .clickable { color = c }
                                .then(if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, color) }, enabled = name.isNotBlank() && !busy) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
private fun FileInfoRow(file: PickedFile, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${file.name.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }} · " +
                    "${formatBytes(file.size)} · ${file.mime}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun QueueCard(
    job: UploadJob,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.fileName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                Text(statusLabel(job.status), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                listOfNotNull(job.chatTitle, job.topicName).joinToString(" › "),
                style = MaterialTheme.typography.bodySmall
            )

            if (job.status == UploadStatus.UPLOADING || job.status == UploadStatus.COMPLETED) {
                val pct = if (job.sizeBytes > 0) job.uploadedBytes.toFloat() / job.sizeBytes else 0f
                LinearProgressIndicator(progress = pct.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Text(
                    "${(pct * 100).toInt()}% · ${formatBytes(job.uploadedBytes)} / ${formatBytes(job.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (job.status == UploadStatus.UPLOADING) {
                    Text(
                        "Speed ${formatBytes(job.speedBps.toLong())}/s · Remaining ${formatEta(job.etaSeconds)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (job.status == UploadStatus.COMPLETED) {
                Text(
                    "Message ${job.messageId ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )
                job.link?.let { link ->
                    Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) { Text("Copy link") }
                        TextButton(onClick = { onShare(link) }) { Text("Share") }
                        TextButton(onClick = { onOpen(link) }) { Text("Open") }
                    }
                }
            }

            job.error?.let { ErrorCard(it) }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (job.status) {
                    UploadStatus.WAITING, UploadStatus.UPLOADING -> TextButton(onClick = onCancel) { Text("Cancel") }
                    UploadStatus.FAILED, UploadStatus.CANCELLED -> TextButton(onClick = onRetry) { Text("Retry") }
                    UploadStatus.COMPLETED -> Unit
                }
            }
        }
    }
}

private fun statusLabel(status: UploadStatus): String = when (status) {
    UploadStatus.WAITING -> "Waiting"
    UploadStatus.UPLOADING -> "Uploading"
    UploadStatus.COMPLETED -> "Completed"
    UploadStatus.FAILED -> "Failed"
    UploadStatus.CANCELLED -> "Cancelled"
}

private fun formatEta(seconds: Long?): String {
    if (seconds == null) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return "%02dm %02ds".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

private fun openLink(context: Context, link: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun shareLink(context: Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Upload tab: shows the tab's default channel with its pins and search, then the file picker,
 * caption and queue. Files go to the default channel.
 */
@Composable
fun UploadPane(
    onCreate: () -> Unit,
    home: TelegramHomeViewModel = hiltViewModel(),
    vm: UploadViewModel = hiltViewModel()
) {
    val current by home.uploadDefault.collectAsStateWithLifecycle()
    val pins by home.uploadPins.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf(true) }

    // Uploads always go to the tab's default channel.
    LaunchedEffect(current?.chatId) {
        current?.let { vm.pickDestination(it) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DestinationSection(HomeTab.UPLOAD, current, pins, onCreate, home) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Files (${files.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (files.isNotEmpty()) TextButton(onClick = { vm.clearFiles() }) { Text("Clear") }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose files")
                    }
                    files.forEach { f -> FileInfoRow(f) { vm.removeFile(f.uri) } }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (e.g. backup, apk)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Add \"Uploaded via Paradox Uploader\"", modifier = Modifier.weight(1f))
                        Switch(checked = footer, onCheckedChange = { footer = it })
                    }
                }
            }
        }

        item {
            Button(
                onClick = { vm.startUploads(caption, tags, footer) },
                enabled = files.isNotEmpty() && current != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        current == null -> "Pick a default channel first"
                        files.isEmpty() -> "Choose files to upload"
                        else -> "Upload ${files.size} file(s) to ${current!!.title}"
                    }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setPaused(!paused) }) { Text(if (paused) "Resume" else "Pause") }
                TextButton(onClick = { vm.clearFinished() }) { Text("Clear done") }
            }
        }
        if (queue.isEmpty()) {
            item { Text("Nothing queued yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(queue, key = { it.id }) { job ->
            QueueCard(
                job = job,
                onRetry = { vm.retry(job.id) },
                onCancel = { vm.cancel(job.id) },
                onOpen = { openLink(context, it) },
                onShare = { shareLink(context, it) }
            )
        }
    }
}

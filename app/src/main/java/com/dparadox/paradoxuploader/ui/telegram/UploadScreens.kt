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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.data.telegram.HomeTab
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.DestinationKind
import com.dparadox.paradoxuploader.domain.telegram.ForumTopic
import com.dparadox.paradoxuploader.domain.telegram.PickedFile
import com.dparadox.paradoxuploader.domain.telegram.UploadJob
import com.dparadox.paradoxuploader.domain.telegram.UploadStatus
import com.dparadox.paradoxuploader.ui.components.EmptyState
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InitialAvatar
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.SectionHeader
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.SwitchRow
import com.dparadox.paradoxuploader.ui.components.TonePill

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
        actions = {
            IconButton(onClick = { vm.loadDestinations() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilledTonalButton(onClick = { showChannel = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("New channel")
                }
                FilledTonalButton(onClick = { showGroup = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("New group")
                }
            }
            state.error?.let { ErrorCard(it, Modifier.padding(horizontal = Spacing.screen)) }
            if (state.loading) {
                CenteredLoading("Loading your chats…")
            } else if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Forum,
                    title = "No chats yet",
                    body = "No channels or groups yet. Create one above."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(state.items, key = { it.chatId }) { dest ->
                        ParadoxCard(
                            onClick = {
                                vm.pickDestination(dest)
                                onPicked()
                            },
                            contentPadding = PaddingValues(Spacing.lg)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                InitialAvatar(dest.title)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        dest.title.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${dest.typeLabel} · ID ${dest.chatId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    destinationIcon(dest.kind),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun destinationIcon(kind: DestinationKind) = when (kind) {
    DestinationKind.FORUM -> Icons.Rounded.Forum
    DestinationKind.CHANNEL -> Icons.Rounded.Campaign
    else -> Icons.Rounded.Groups
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
        icon = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
        title = { Text("New channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public channel", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                if (isPublic) {
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, prefix = { Text("@") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
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
        icon = { Icon(Icons.Rounded.Groups, contentDescription = null) },
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Forum group (topics)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (forum) "Supports topics." else "Basic group, no topics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

// ---------------------------------------------------------------- Shared upload pieces

/** Dashed drop-zone style picker plus the list of chosen files. */
@Composable
private fun FilesPickerCard(
    files: List<PickedFile>,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onRemove: (PickedFile) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ParadoxCard(spacing = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (files.isNotEmpty()) {
                TonePill(text = files.size.toString(), container = scheme.primary, content = scheme.onPrimary, dot = false)
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        Surface(
            onClick = onPick,
            shape = MaterialTheme.shapes.large,
            color = scheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, scheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.large)
        ) {
            Column(
                Modifier.padding(vertical = Spacing.xl, horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                IconTile(Icons.Rounded.AttachFile, size = 48.dp, container = scheme.primary, content = scheme.onPrimary, circle = true)
                Text("Choose files", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
            }
        }
        files.forEach { f -> FileInfoRow(f) { onRemove(f) } }
    }
}

@Composable
private fun CaptionCard(
    caption: String,
    onCaption: (String) -> Unit,
    tags: String,
    onTags: (String) -> Unit,
    footer: Boolean,
    onFooter: (Boolean) -> Unit
) {
    ParadoxCard(spacing = Spacing.md) {
        Text("Message", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = caption,
            onValueChange = onCaption,
            label = { Text("Caption") },
            minLines = 2,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = tags,
            onValueChange = onTags,
            label = { Text("Tags (e.g. backup, apk)") },
            prefix = { Text("#") },
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        SwitchRow(
            title = "Add \"Uploaded via Paradox Uploader\"",
            subtitle = null,
            checked = footer,
            onChecked = onFooter
        )
    }
}

@Composable
private fun QueueHeader(paused: Boolean, onTogglePause: () -> Unit, onClearDone: () -> Unit, pauseLabel: String) {
    SectionHeader(
        title = "Queue",
        action = {
            IconButton(onClick = onTogglePause) {
                Icon(
                    if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = pauseLabel
                )
            }
            TextButton(onClick = onClearDone) {
                Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Clear done")
            }
        }
    )
}

@Composable
private fun QueueEmpty() {
    ParadoxCard(contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(
                Icons.Rounded.Inbox,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                circle = true
            )
            Text(
                "Nothing queued yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Destination
            item {
                ParadoxCard(spacing = Spacing.md) {
                    if (dest == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            IconTile(
                                Icons.Rounded.Forum,
                                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                                circle = true
                            )
                            Text("No destination selected", style = MaterialTheme.typography.titleMedium)
                        }
                        PrimaryButton(text = "Choose destination", onClick = onChooseDestination)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            InitialAvatar(
                                dest.title,
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Column(Modifier.weight(1f)) {
                                Text(dest.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    dest.typeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(onClick = onChooseDestination) { Text("Change") }
                        }
                    }
                    if (recent.size > 1) {
                        Text("Recent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(recent, key = { it.chatId }) { r ->
                                AssistChip(
                                    onClick = { vm.pickDestination(r) },
                                    label = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                }
            }

            // Topics
            if (dest?.supportsTopics == true) {
                item {
                    ParadoxCard(spacing = Spacing.sm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Topic", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (topics.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            IconButton(onClick = { vm.loadTopics(dest.chatId) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                            TextButton(onClick = { showNewTopic = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("New topic")
                            }
                        }
                        topics.error?.let { ErrorCard(it) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            item {
                                TopicChip(selected = selection.topic == null, label = "General") { vm.selectTopic(null) }
                            }
                            items(topics.items, key = { it.threadId }) { t ->
                                TopicChip(selected = selection.topic?.threadId == t.threadId, label = t.name) { vm.selectTopic(t) }
                            }
                        }
                    }
                }
            }

            // Files
            item {
                FilesPickerCard(
                    files = files,
                    onPick = { picker.launch(arrayOf("*/*")) },
                    onClear = { vm.clearFiles() },
                    onRemove = { vm.removeFile(it.uri) }
                )
            }

            // Caption
            item {
                CaptionCard(caption, { caption = it }, tags, { tags = it }, footer, { footer = it })
            }

            item {
                PrimaryButton(
                    text = if (files.isEmpty()) "Start upload" else "Upload ${files.size} file(s)",
                    onClick = { vm.startUploads(caption, tags, footer) },
                    enabled = files.isNotEmpty() && dest != null,
                    icon = Icons.Rounded.CloudUpload
                )
            }

            // Queue
            item {
                QueueHeader(
                    paused = paused,
                    onTogglePause = { vm.setPaused(!paused) },
                    onClearDone = { vm.clearFinished() },
                    pauseLabel = if (paused) "Resume queue" else "Pause queue"
                )
            }
            if (queue.isEmpty()) {
                item { QueueEmpty() }
            }
            items(queue, key = { it.id }) { job ->
                QueueCard(
                    job = job,
                    onRetry = { vm.retry(job.id) },
                    onCancel = { vm.cancel(job.id) },
                    onOpen = { openLink(context, it) },
                    onShare = { shareLink(context, it) },
                    modifier = Modifier.animateItem()
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
private fun TopicChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = CircleShape
    )
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
        icon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
        title = { Text("New topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(name, { name = it }, label = { Text("Topic name") }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                Text("Icon colour (optional)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TOPIC_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF000000L or c.toLong()))
                                .clickable { color = c }
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (c == color) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
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
    val ext = file.name.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            Icons.AutoMirrored.Rounded.InsertDriveFile,
            size = 36.dp,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$ext · ${formatBytes(file.size)} · ${file.mime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun jobStatusColors(status: UploadStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        UploadStatus.WAITING -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        UploadStatus.UPLOADING -> scheme.primaryContainer to scheme.onPrimaryContainer
        UploadStatus.COMPLETED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UploadStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
        UploadStatus.CANCELLED -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

@Composable
private fun QueueCard(
    job: UploadJob,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val (pillContainer, pillContent) = jobStatusColors(job.status)
    ParadoxCard(modifier = modifier, contentPadding = PaddingValues(Spacing.lg), spacing = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(
                Icons.AutoMirrored.Rounded.InsertDriveFile,
                size = 44.dp,
                container = pillContainer,
                content = pillContent
            )
            Column(Modifier.weight(1f)) {
                Text(job.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(job.chatTitle, job.topicName).joinToString(" › "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TonePill(text = statusLabel(job.status), container = pillContainer, content = pillContent)
        }

        if (job.status == UploadStatus.UPLOADING || job.status == UploadStatus.COMPLETED) {
            val pct = if (job.sizeBytes > 0) job.uploadedBytes.toFloat() / job.sizeBytes else 0f
            ParadoxProgress(
                progress = { pct.coerceIn(0f, 1f) },
                color = if (job.status == UploadStatus.COMPLETED) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary
            )
            Text(
                "${(pct * 100).toInt()}% · ${formatBytes(job.uploadedBytes)} / ${formatBytes(job.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (job.status == UploadStatus.UPLOADING) {
                Text(
                    "Speed ${formatBytes(job.speedBps.toLong())}/s · Remaining ${formatEta(job.etaSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (job.status == UploadStatus.COMPLETED) {
            Text(
                "Message ${job.messageId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            job.link?.let { link ->
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilledTonalButton(
                        onClick = { clipboard.setText(AnnotatedString(link)) },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Copy link")
                    }
                    IconButton(onClick = { onShare(link) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { onOpen(link) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open")
                    }
                }
            }
        }

        job.error?.let { ErrorCard(it) }

        when (job.status) {
            UploadStatus.WAITING, UploadStatus.UPLOADING -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            UploadStatus.FAILED, UploadStatus.CANCELLED -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Retry")
                }
            }
            UploadStatus.COMPLETED -> Unit
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
    com.dparadox.paradoxuploader.ui.util.UiActions.open(context, link)
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
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item { DestinationSection(HomeTab.UPLOAD, current, pins, onCreate, home) }

        item {
            FilesPickerCard(
                files = files,
                onPick = { picker.launch(arrayOf("*/*")) },
                onClear = { vm.clearFiles() },
                onRemove = { vm.removeFile(it.uri) }
            )
        }

        item {
            CaptionCard(caption, { caption = it }, tags, { tags = it }, footer, { footer = it })
        }

        item {
            val target = current
            PrimaryButton(
                text = when {
                    target == null -> "Pick a default channel first"
                    files.isEmpty() -> "Choose files to upload"
                    else -> "Upload ${files.size} file(s) to ${target.title}"
                },
                onClick = { vm.startUploads(caption, tags, footer) },
                enabled = files.isNotEmpty() && target != null,
                icon = Icons.Rounded.CloudUpload
            )
        }

        item {
            QueueHeader(
                paused = paused,
                onTogglePause = { vm.setPaused(!paused) },
                onClearDone = { vm.clearFinished() },
                pauseLabel = if (paused) "Resume" else "Pause"
            )
        }
        if (queue.isEmpty()) {
            item { QueueEmpty() }
        }
        items(queue, key = { it.id }) { job ->
            QueueCard(
                job = job,
                onRetry = { vm.retry(job.id) },
                onCancel = { vm.cancel(job.id) },
                onOpen = { openLink(context, it) },
                onShare = { shareLink(context, it) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

package com.dparadox.paradoxuploader.ui.upload

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.EnqueueError
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadProgress
import com.dparadox.paradoxuploader.domain.model.UploadStats
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.domain.util.Licenses
import com.dparadox.paradoxuploader.ui.components.BannerTone
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.FileThumb
import com.dparadox.paradoxuploader.ui.components.HeroCard
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InfoBanner
import com.dparadox.paradoxuploader.ui.components.LinkActions
import com.dparadox.paradoxuploader.ui.components.LocalSnackbarHost
import com.dparadox.paradoxuploader.ui.components.LogoMark
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.ParadoxScaffold
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.QrDialog
import com.dparadox.paradoxuploader.ui.components.SectionHeader
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.StatusChip
import com.dparadox.paradoxuploader.ui.components.TonePill
import com.dparadox.paradoxuploader.ui.components.statusColors
import com.dparadox.paradoxuploader.ui.util.UiActions
import com.dparadox.paradoxuploader.ui.util.errorText
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(onOpenSettings: () -> Unit, viewModel: UploadViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var qrUrl by remember { mutableStateOf<String?>(null) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editorFolder by remember { mutableStateOf<Folder?>(null) }
    var showDuplicates by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addUris(uris.map { it.toString() })
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addTree(uri.toString())
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var showNewFolder by remember { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.id == viewModel.selectedFolderId }
    // Re-read on every recomposition so it is correct again after coming back from system settings.
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val requestNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val startNow: () -> Unit = {
        scope.launch {
            val result = viewModel.startUpload()
            when (result.error) {
                EnqueueError.NO_KEYS -> {
                    val r = snackbar.showSnackbar(
                        message = context.getString(R.string.add_keys_first),
                        actionLabel = context.getString(R.string.settings)
                    )
                    if (r == SnackbarResult.ActionPerformed) onOpenSettings()
                }
                EnqueueError.INVALID_IDENTIFIER -> {
                    val r = snackbar.showSnackbar(
                        message = context.getString(R.string.invalid_identifier_setting),
                        actionLabel = context.getString(R.string.settings)
                    )
                    if (r == SnackbarResult.ActionPerformed) onOpenSettings()
                }
                null -> if (result.skipped > 0) {
                    snackbar.showSnackbar(
                        context.getString(R.string.files_skipped, result.skipped)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.receiveAsFlow().collect { message ->
            snackbar.showSnackbar(context.getString(message.textRes, *message.args.toTypedArray()))
        }
    }

    ParadoxScaffold(
        title = stringResource(R.string.app_name),
        navigationIcon = { LogoMark(Modifier.padding(start = Spacing.md, end = Spacing.xs), size = 36.dp) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Column {
                        HeroCard {
                            IconTile(
                                Icons.Rounded.CloudUpload,
                                size = 56.dp,
                                container = MaterialTheme.colorScheme.primary,
                                content = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                stringResource(R.string.upload_hero_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                stringResource(R.string.upload_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.size(Spacing.xs))
                            PrimaryButton(
                                text = stringResource(R.string.select_files),
                                onClick = { picker.launch(arrayOf("*/*")) },
                                icon = Icons.Rounded.AttachFile
                            )
                            FilledTonalButton(
                                onClick = { treePicker.launch(null) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(Spacing.sm))
                                Text(stringResource(R.string.folder_upload_device), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (!notificationsOn) {
                    item {
                        NotificationBanner(
                            onAllow = requestNotifications.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
                            onSettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        )
                    }
                }

                item {
                    FolderCard(
                        folders = folders,
                        selectedId = viewModel.selectedFolderId,
                        onSelect = { viewModel.selectedFolderId = it },
                        emptyCount = folders.count { it.fileCount == 0 },
                        onRemoveEmpty = viewModel::removeEmptyFolders,
                        onNew = { editorFolder = null; showNewFolder = true },
                        onEdit = { editorFolder = it; showNewFolder = true },
                        onCopy = { UiActions.copy(context, it.itemUrl) },
                        onOpen = { UiActions.open(context, it.itemUrl) },
                        onDelete = { viewModel.deleteFolder(it.id) }
                    )
                }

                if (pending.isNotEmpty()) {
                    item {
                        ParadoxCard(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            spacing = Spacing.md
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.ready_to_upload),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TonePill(
                                    text = pending.size.toString(),
                                    container = MaterialTheme.colorScheme.primary,
                                    content = MaterialTheme.colorScheme.onPrimary,
                                    dot = false
                                )
                            }
                            pending.take(5).forEach { file ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconTile(
                                        if (file.duplicate) Icons.Rounded.Warning else Icons.Rounded.AttachFile,
                                        size = 36.dp,
                                        container = if (file.duplicate) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        content = if (file.duplicate) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (file.duplicate) {
                                                "${Formatters.size(file.sizeBytes)} · ${stringResource(R.string.duplicate_badge)}"
                                            } else {
                                                Formatters.size(file.sizeBytes)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (file.duplicate) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removePending(file.uri) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove))
                                    }
                                }
                            }
                            if (pending.size > 5) {
                                Text(
                                    stringResource(R.string.and_more, pending.size - 5),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Spacing.xs)
                                )
                            }
                            Text(
                                stringResource(R.string.pending_metadata),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs)
                            )
                            OutlinedTextField(
                                value = viewModel.title,
                                onValueChange = { viewModel.title = it },
                                label = { Text(stringResource(R.string.title_optional)) },
                                singleLine = true,
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = viewModel.description,
                                onValueChange = { viewModel.description = it },
                                label = { Text(stringResource(R.string.description_optional)) },
                                minLines = 2,
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = viewModel.tags,
                                onValueChange = { viewModel.tags = it },
                                label = { Text(stringResource(R.string.tags_optional)) },
                                supportingText = { Text(stringResource(R.string.tags_hint)) },
                                singleLine = true,
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (selectedFolder?.hasDefaults == true) {
                                Text(
                                    stringResource(R.string.folder_defaults_used),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PrimaryButton(
                                text = if (selectedFolder != null) {
                                    stringResource(R.string.upload_n_files_to_folder, pending.size, selectedFolder.name)
                                } else {
                                    stringResource(R.string.upload_n_files, pending.size)
                                },
                                onClick = { if (pending.any { it.duplicate }) showDuplicates = true else startNow() },
                                icon = Icons.Rounded.CloudUpload
                            )
                        }
                    }
                }

                item { StatsCard(stats) }

                if (settings.uploadsPaused) {
                    item { PausedBanner(onResume = viewModel::resumeAll) }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.queue),
                        action = {
                            if (settings.uploadsPaused) {
                                FilledTonalButton(onClick = viewModel::resumeAll) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.resume_all))
                                }
                            } else if (recent.any { it.status == UploadStatus.UPLOADING }) {
                                FilledTonalButton(onClick = viewModel::pauseAll) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.pause_all))
                                }
                            }
                        }
                    )
                }
                if (recent.isEmpty()) {
                    item {
                        ParadoxCard(contentPadding = PaddingValues(Spacing.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                IconTile(
                                    Icons.Rounded.Inbox,
                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    circle = true
                                )
                                Text(
                                    stringResource(R.string.queue_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(recent, key = { it.id }) { item ->
                        UploadQueueCard(
                            item = item,
                            progress = progress[item.id],
                            paused = settings.uploadsPaused,
                            chargingOnly = settings.chargingOnly,
                            onCancel = { viewModel.cancel(item.id) },
                            onRetry = { viewModel.retry(item.id) },
                            onCheckNow = { viewModel.checkNow(item.id) },
                            onQr = { qrUrl = item.directUrl },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    qrUrl?.let { QrDialog(url = it, onDismiss = { qrUrl = null }) }

    if (showNewFolder) {
        FolderEditorDialog(
            initial = editorFolder,
            onDismiss = { showNewFolder = false },
            onSave = { name, meta ->
                viewModel.saveFolder(editorFolder?.id, name, meta)
                showNewFolder = false
            }
        )
    }

    if (showDuplicates) {
        AlertDialog(
            onDismissRequest = { showDuplicates = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.duplicates_title)) },
            text = { Text(stringResource(R.string.duplicates_message, pending.count { it.duplicate })) },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicates = false
                    startNow()
                }) { Text(stringResource(R.string.duplicates_upload_anyway)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDuplicates = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        showDuplicates = false
                        viewModel.removeDuplicates()
                        if (pending.any { !it.duplicate }) startNow()
                    }) { Text(stringResource(R.string.duplicates_skip)) }
                }
            }
        )
    }
}

@Composable
private fun NotificationBanner(onAllow: (() -> Unit)?, onSettings: () -> Unit) {
    InfoBanner(
        icon = Icons.Rounded.NotificationsOff,
        title = stringResource(R.string.notif_banner_title),
        body = stringResource(R.string.notif_banner_body),
        tone = BannerTone.ACCENT,
        actions = {
            if (onAllow != null) {
                Button(onClick = onAllow) { Text(stringResource(R.string.notif_banner_allow)) }
            }
            OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
        }
    )
}

@Composable
private fun FolderCard(
    folders: List<Folder>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    emptyCount: Int,
    onRemoveEmpty: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Folder) -> Unit,
    onCopy: (Folder) -> Unit,
    onOpen: (Folder) -> Unit,
    onDelete: (Folder) -> Unit
) {
    ParadoxCard(spacing = Spacing.xs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(
                Icons.Rounded.Folder,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.folders_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.folders_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        FolderOption(
            selected = selectedId == null,
            onSelect = { onSelect(null) },
            icon = Icons.Rounded.FolderOff,
            title = stringResource(R.string.folder_none),
            subtitle = null
        )
        folders.forEach { folder ->
            FolderOption(
                selected = selectedId == folder.id,
                onSelect = { onSelect(folder.id) },
                icon = Icons.Rounded.Folder,
                title = folder.name,
                subtitle = listOfNotNull(
                    stringResource(R.string.folder_files, folder.fileCount),
                    Licenses.labelFor(folder.licenseUrl)
                ).joinToString(" · "),
                trailing = {
                    FolderMenu(
                        onEdit = { onEdit(folder) },
                        onCopy = { onCopy(folder) },
                        onOpen = { onOpen(folder) },
                        onDelete = { onDelete(folder) }
                    )
                }
            )
        }
        if (emptyCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.xs)) {
                Text(
                    stringResource(R.string.cleanup_empty_folders, emptyCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRemoveEmpty) { Text(stringResource(R.string.cleanup_remove_now)) }
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text(stringResource(R.string.folder_new))
        }
    }
}

/** Selectable folder row: highlighted container when selected, radio button for accessibility. */
@Composable
private fun FolderOption(
    selected: Boolean,
    onSelect: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = Spacing.md))
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing() else Spacer(Modifier.width(Spacing.md))
    }
}

@Composable
private fun UploadQueueCard(
    item: UploadItem,
    progress: UploadProgress?,
    paused: Boolean,
    chargingOnly: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCheckNow: () -> Unit,
    onQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusContainer, _) = statusColors(item.status)
    ParadoxCard(modifier = modifier, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FileThumb(item)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    Formatters.size(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(item.status)
        }
        when (item.status) {
            UploadStatus.UPLOADING -> {
                if (progress != null && progress.totalBytes > 0) {
                    ParadoxProgress(progress = { progress.fraction })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                R.string.progress_line,
                                (progress.fraction * 100).toInt(),
                                Formatters.speed(progress.speedBps),
                                Formatters.eta(progress.etaSeconds)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    if (!paused) ParadoxProgress(progress = null)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                when {
                                    paused -> R.string.paused_hint
                                    chargingOnly -> R.string.waiting_for_charger
                                    else -> R.string.waiting_to_start
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            UploadStatus.PROCESSING -> {
                ParadoxProgress(progress = null, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.processing_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCheckNow) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.check_now))
                    }
                }
            }
            UploadStatus.DELAYED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.delayed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCheckNow) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.check_now))
                    }
                }
            }
            UploadStatus.FAILED -> {
                Surface(shape = MaterialTheme.shapes.medium, color = statusContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            errorText(item.errorMessage),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.retry))
                    }
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.remove)) }
                }
            }
            UploadStatus.READY -> LinkActions(item = item, onQr = onQr)
        }
    }
}

@Composable
private fun FolderMenu(onEdit: () -> Unit, onCopy: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = { open = false; onCopy() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_item_page)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                onClick = { open = false; onOpen() }
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_remove), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

@Composable
private fun StatsCard(stats: UploadStats) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(title = stringResource(R.string.stats_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                label = stringResource(R.string.stats_total),
                files = stats.totalFiles,
                bytes = stats.totalBytes,
                icon = Icons.Rounded.BarChart,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.stats_month),
                files = stats.monthFiles,
                bytes = stats.monthBytes,
                icon = Icons.Rounded.CalendarMonth,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.stats_today),
                files = stats.todayFiles,
                bytes = stats.todayBytes,
                icon = Icons.Rounded.Today,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
        if (stats.failedFiles > 0) {
            Text(
                stringResource(R.string.stats_failed, stats.failedFiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs)
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    files: Int,
    bytes: Long,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f), maxLines = 1)
            Text(Formatters.size(bytes), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                stringResource(R.string.stats_files, files),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    InfoBanner(
        icon = Icons.Rounded.Pause,
        title = stringResource(R.string.paused_title),
        body = stringResource(R.string.paused_body),
        tone = BannerTone.NEUTRAL,
        actions = {
            Button(onClick = onResume) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.resume_all))
            }
        }
    )
}

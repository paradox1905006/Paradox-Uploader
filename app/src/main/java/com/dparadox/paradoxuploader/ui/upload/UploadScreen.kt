package com.dparadox.paradoxuploader.ui.upload

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.dparadox.paradoxuploader.ui.components.FileThumb
import com.dparadox.paradoxuploader.ui.components.LinkActions
import com.dparadox.paradoxuploader.ui.components.LocalSnackbarHost
import com.dparadox.paradoxuploader.ui.components.QrDialog
import com.dparadox.paradoxuploader.ui.components.StatusChip
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_paradox),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.app_name))
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.upload_intro), style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.select_files))
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
                        onPickDeviceFolder = { treePicker.launch(null) },
                        onCopy = { UiActions.copy(context, it.itemUrl) },
                        onOpen = { UiActions.open(context, it.itemUrl) },
                        onDelete = { viewModel.deleteFolder(it.id) }
                    )
                }

                if (pending.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.ready_to_upload), style = MaterialTheme.typography.titleMedium)
                                pending.take(5).forEach { file ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                Formatters.size(file.sizeBytes),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (file.duplicate) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.duplicate_badge),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = { viewModel.removePending(file.uri) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove))
                                        }
                                    }
                                }
                                if (pending.size > 5) {
                                    Text(
                                        stringResource(R.string.and_more, pending.size - 5),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedTextField(
                                    value = viewModel.title,
                                    onValueChange = { viewModel.title = it },
                                    label = { Text(stringResource(R.string.title_optional)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = viewModel.description,
                                    onValueChange = { viewModel.description = it },
                                    label = { Text(stringResource(R.string.description_optional)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = viewModel.tags,
                                    onValueChange = { viewModel.tags = it },
                                    label = { Text(stringResource(R.string.tags_optional)) },
                                    supportingText = { Text(stringResource(R.string.tags_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (selectedFolder?.hasDefaults == true) {
                                    Text(
                                        stringResource(R.string.folder_defaults_used),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { if (pending.any { it.duplicate }) showDuplicates = true else startNow() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (selectedFolder != null) {
                                            stringResource(R.string.upload_n_files_to_folder, pending.size, selectedFolder.name)
                                        } else {
                                            stringResource(R.string.upload_n_files, pending.size)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item { StatsCard(stats) }

                if (settings.uploadsPaused) {
                    item { PausedBanner(onResume = viewModel::resumeAll) }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.queue),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (settings.uploadsPaused) {
                            TextButton(onClick = viewModel::resumeAll) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.resume_all))
                            }
                        } else if (recent.any { it.status == UploadStatus.UPLOADING }) {
                            TextButton(onClick = viewModel::pauseAll) {
                                Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.pause_all))
                            }
                        }
                    }
                }
                if (recent.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.queue_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            onQr = { qrUrl = item.directUrl }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.NotificationsOff, contentDescription = null)
                Text(stringResource(R.string.notif_banner_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.notif_banner_body), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onAllow != null) {
                    Button(onClick = onAllow) { Text(stringResource(R.string.notif_banner_allow)) }
                }
                OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folders: List<Folder>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    emptyCount: Int,
    onRemoveEmpty: () -> Unit,
    onNew: () -> Unit,
    onPickDeviceFolder: () -> Unit,
    onEdit: (Folder) -> Unit,
    onCopy: (Folder) -> Unit,
    onOpen: (Folder) -> Unit,
    onDelete: (Folder) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.folders_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.folders_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(null) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedId == null, onClick = { onSelect(null) })
                Text(
                    stringResource(R.string.folder_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(folder.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedId == folder.id, onClick = { onSelect(folder.id) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                stringResource(R.string.folder_files, folder.fileCount),
                                Licenses.labelFor(folder.licenseUrl)
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FolderMenu(
                        onEdit = { onEdit(folder) },
                        onCopy = { onCopy(folder) },
                        onOpen = { onOpen(folder) },
                        onDelete = { onDelete(folder) }
                    )
                }
            }
            if (emptyCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.cleanup_empty_folders, emptyCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRemoveEmpty) { Text(stringResource(R.string.cleanup_remove_now)) }
                }
            }
            Spacer(Modifier.size(4.dp))
            Button(onClick = onPickDeviceFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.folder_upload_device))
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.folder_new))
            }
        }
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
    onQr: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileThumb(item)
                Column(Modifier.weight(1f)) {
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
                        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(
                                R.string.progress_line,
                                (progress.fraction * 100).toInt(),
                                Formatters.speed(progress.speedBps),
                                Formatters.eta(progress.etaSeconds)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        if (!paused) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(
                                when {
                                    paused -> R.string.paused_hint
                                    chargingOnly -> R.string.waiting_for_charger
                                    else -> R.string.waiting_to_start
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
                UploadStatus.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.processing_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCheckNow) { Text(stringResource(R.string.check_now)) }
                }
                UploadStatus.DELAYED -> {
                    Text(stringResource(R.string.delayed_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCheckNow) { Text(stringResource(R.string.check_now)) }
                }
                UploadStatus.FAILED -> {
                    Text(errorText(item.errorMessage), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.remove)) }
                    }
                }
                UploadStatus.READY -> LinkActions(item = item, onQr = onQr)
            }
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_remove)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

@Composable
private fun StatsCard(stats: UploadStats) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatColumn(stringResource(R.string.stats_total), stats.totalFiles, stats.totalBytes, Modifier.weight(1f))
                StatColumn(stringResource(R.string.stats_month), stats.monthFiles, stats.monthBytes, Modifier.weight(1f))
                StatColumn(stringResource(R.string.stats_today), stats.todayFiles, stats.todayBytes, Modifier.weight(1f))
            }
            if (stats.failedFiles > 0) {
                Text(
                    stringResource(R.string.stats_failed, stats.failedFiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, files: Int, bytes: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Formatters.size(bytes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.stats_files, files), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Pause, contentDescription = null)
                Text(stringResource(R.string.paused_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.paused_body), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onResume) { Text(stringResource(R.string.resume_all)) }
        }
    }
}

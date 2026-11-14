@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dparadox.paradoxuploader.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.FileType
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.ui.components.DetailField
import com.dparadox.paradoxuploader.ui.components.EmptyState
import com.dparadox.paradoxuploader.ui.components.FileRow
import com.dparadox.paradoxuploader.ui.components.FileThumb
import com.dparadox.paradoxuploader.ui.components.LinkActions
import com.dparadox.paradoxuploader.ui.components.ParadoxScaffold
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.QrDialog
import com.dparadox.paradoxuploader.ui.components.SearchPill
import com.dparadox.paradoxuploader.ui.components.SkeletonBlocks
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.StatusChip
import com.dparadox.paradoxuploader.ui.components.icon
import com.dparadox.paradoxuploader.ui.components.FileRowActions
import com.dparadox.paradoxuploader.ui.components.LinksMenu
import com.dparadox.paradoxuploader.ui.components.LocalSnackbarHost
import com.dparadox.paradoxuploader.ui.util.UiActions
import com.dparadox.paradoxuploader.ui.util.formatDateTime
import kotlinx.coroutines.flow.receiveAsFlow

private data class DeleteRequest(val ids: List<Long>, val fromSwipe: Boolean)

private fun FileType.labelRes(): Int = when (this) {
    FileType.IMAGE -> R.string.type_image
    FileType.VIDEO -> R.string.type_video
    FileType.AUDIO -> R.string.type_audio
    FileType.DOCUMENT -> R.string.type_document
    FileType.ARCHIVE -> R.string.type_archive
    FileType.OTHER -> R.string.type_other
}

private fun UploadStatus.labelRes(): Int = when (this) {
    UploadStatus.UPLOADING -> R.string.status_uploading
    UploadStatus.PROCESSING -> R.string.status_processing
    UploadStatus.READY -> R.string.status_ready
    UploadStatus.DELAYED -> R.string.status_delayed
    UploadStatus.FAILED -> R.string.status_failed
}

@Composable
fun FilesScreen(
    scrollToId: Long?,
    onScrollHandled: () -> Unit,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var linksMenuOpen by remember { mutableStateOf(false) }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val listState = rememberLazyListState()
    val selecting = selection.isNotEmpty()
    val items = (state as? FilesUiState.Content)?.items.orEmpty()

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LaunchedEffect(Unit) {
        viewModel.messages.receiveAsFlow().collect { message ->
            val result = snackbar.showSnackbar(
                message = context.getString(message.textRes, *message.args.toTypedArray()),
                actionLabel = message.actionRes?.let { context.getString(it) },
                duration = if (message.onAction != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    LaunchedEffect(scrollToId, items) {
        if (scrollToId != null && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.id == scrollToId }
            if (index >= 0) listState.animateScrollToItem(index)
            onScrollHandled()
        }
    }

    ParadoxScaffold(
        title = if (selecting) stringResource(R.string.selected_count, selection.size)
        else stringResource(R.string.nav_files),
        navigationIcon = {
            if (selecting) {
                IconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_selection))
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { linksMenuOpen = true }) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.links_menu))
                }
                LinksMenu(
                    expanded = linksMenuOpen,
                    onDismiss = { linksMenuOpen = false },
                    items = if (selecting) items.filter { it.id in selection } else items,
                    folderNames = folderNames
                )
            }
            if (selecting) {
                IconButton(onClick = { deleteRequest = DeleteRequest(selection.toList(), fromSwipe = false) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_selected))
                }
            } else {
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort))
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.labelRes)) },
                                trailingIcon = {
                                    if (filters.sort == order) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = { viewModel.setSort(order); sortMenuOpen = false }
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                SearchPill(
                    value = filters.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.search_files),
                    clearDescription = stringResource(R.string.clear_search),
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (folders.isNotEmpty()) {
                        item {
                            FolderFilterChip(
                                folders = folders,
                                selectedId = filters.folderId,
                                onSelect = viewModel::setFolder
                            )
                        }
                    }
                    items(UploadStatus.entries.toList()) { status ->
                        val selected = status in filters.statuses
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleStatus(status) },
                            label = { Text(stringResource(status.labelRes())) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            shape = CircleShape
                        )
                    }
                    items(FileType.entries.toList()) { type ->
                        val selected = type in filters.types
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleType(type) },
                            label = { Text(stringResource(type.labelRes())) },
                            leadingIcon = {
                                Icon(
                                    if (selected) Icons.Rounded.Check else type.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            shape = CircleShape
                        )
                    }
                }

                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    when (val current = state) {
                        FilesUiState.Loading -> SkeletonBlocks()
                        is FilesUiState.Empty -> MessageState(
                            icon = if (current.filtered) Icons.Rounded.SearchOff else Icons.Rounded.FolderOpen,
                            title = stringResource(if (current.filtered) R.string.no_results else R.string.files_empty_title),
                            body = stringResource(if (current.filtered) R.string.no_results_hint else R.string.files_empty_body)
                        )
                        is FilesUiState.Error -> MessageState(
                            icon = Icons.Rounded.CloudOff,
                            title = stringResource(R.string.files_error_title),
                            body = current.message ?: stringResource(R.string.error_unknown),
                            actionLabel = stringResource(R.string.retry),
                            onAction = viewModel::refresh
                        )
                        is FilesUiState.Content -> LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(current.items, key = { it.id }) { item ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            deleteRequest = DeleteRequest(listOf(item.id), fromSwipe = true)
                                        }
                                        false
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.animateItem(),
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = !selecting,
                                    backgroundContent = {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(MaterialTheme.shapes.large)
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(end = 28.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    stringResource(R.string.delete),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    FileRow(
                                        item = item,
                                        folderName = item.folderId?.let { folderNames[it] },
                                        selected = item.id in selection,
                                        selecting = selecting,
                                        actions = FileRowActions(
                                            onClick = {
                                                if (selecting) viewModel.toggleSelection(item.id) else detailId = item.id
                                            },
                                            onLongClick = { viewModel.toggleSelection(item.id) },
                                            onShowDetails = { detailId = item.id },
                                            onCheckNow = { viewModel.checkNow(item.id) },
                                            onDelete = { deleteRequest = DeleteRequest(listOf(item.id), fromSwipe = false) },
                                            onDownload = { UiActions.download(context, item) }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        deleteRequest = null
                        if (request.fromSwipe) viewModel.scheduleDelete(request.ids) else viewModel.deleteNow(request.ids)
                        viewModel.clearSelection()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteRequest = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val detailItem = items.firstOrNull { it.id == detailId }
    if (detailItem != null) {
        ModalBottomSheet(
            onDismissRequest = { detailId = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            DetailsContent(detailItem)
        }
    }
}

@Composable
private fun DetailsContent(item: UploadItem) {
    val context = LocalContext.current
    var qrUrl by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xxl)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            FileThumb(item, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.fileName, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                StatusChip(item.status)
            }
        }
        if (item.status == UploadStatus.READY && item.directUrl != null) {
            LinkActions(item = item, onQr = { qrUrl = item.directUrl })
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DetailRow(stringResource(R.string.detail_direct_link), item.directUrl ?: stringResource(R.string.link_preparing), item.directUrl)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailRow(stringResource(R.string.detail_item_page), item.itemUrl, item.itemUrl)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailRow(stringResource(R.string.detail_identifier), item.identifier, item.identifier)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DetailRow(stringResource(R.string.detail_size), Formatters.size(item.sizeBytes))
                DetailRow(stringResource(R.string.detail_mime), item.mimeType)
                DetailRow(stringResource(R.string.detail_date), formatDateTime(item.createdAt))
            }
        }
        if (item.status == UploadStatus.READY && item.directUrl != null) {
            PrimaryButton(
                text = stringResource(R.string.open_item_page),
                onClick = { UiActions.open(context, item.itemUrl) },
                icon = Icons.AutoMirrored.Rounded.OpenInNew
            )
        }
    }
    qrUrl?.let { QrDialog(url = it, onDismiss = { qrUrl = null }) }
}

@Composable
private fun DetailRow(label: String, value: String, copyValue: String? = null) {
    val context = LocalContext.current
    DetailField(
        label = label,
        value = value,
        trailing = if (copyValue != null) {
            {
                FilledTonalIconButton(onClick = { UiActions.copy(context, copyValue) }) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy_value, label),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null
    )
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyState(
            icon = icon,
            title = title,
            body = body,
            action = if (actionLabel != null && onAction != null) {
                { Button(onClick = onAction) { Text(actionLabel) } }
            } else null
        )
    }
}

/** Dropdown chip: show all files, only files without a folder, or the files of one folder. */
@Composable
private fun FolderFilterChip(folders: List<Folder>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (selectedId) {
        null -> stringResource(R.string.filter_folder_chip)
        NO_FOLDER_FILTER -> stringResource(R.string.filter_folder_none)
        else -> folders.firstOrNull { it.id == selectedId }?.name ?: stringResource(R.string.filter_folder_chip)
    }
    Box {
        FilterChip(
            selected = selectedId != null,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            shape = CircleShape
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_folder_all)) },
                trailingIcon = { if (selectedId == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = { open = false; onSelect(null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_folder_none)) },
                trailingIcon = { if (selectedId == NO_FOLDER_FILTER) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = { open = false; onSelect(NO_FOLDER_FILTER) }
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text("${folder.name} (${folder.fileCount})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { if (selectedId == folder.id) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(folder.id) }
                )
            }
        }
    }
}

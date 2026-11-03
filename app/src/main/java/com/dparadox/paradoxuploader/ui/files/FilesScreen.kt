@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dparadox.paradoxuploader.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.dparadox.paradoxuploader.ui.components.FileRow
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.selected_count, selection.size)
                        else stringResource(R.string.nav_files)
                    )
                },
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
                                Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.sort))
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes)) },
                                        trailingIcon = {
                                            if (filters.sort == order) Icon(Icons.Rounded.Check, contentDescription = null)
                                        },
                                        onClick = { viewModel.setSort(order); sortMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.search_files)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filters.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        FilterChip(
                            selected = status in filters.statuses,
                            onClick = { viewModel.toggleStatus(status) },
                            label = { Text(stringResource(status.labelRes())) }
                        )
                    }
                    items(FileType.entries.toList()) { type ->
                        FilterChip(
                            selected = type in filters.types,
                            onClick = { viewModel.toggleType(type) },
                            label = { Text(stringResource(type.labelRes())) }
                        )
                    }
                }

                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    when (val current = state) {
                        FilesUiState.Loading -> SkeletonList()
                        is FilesUiState.Empty -> MessageState(
                            title = stringResource(if (current.filtered) R.string.no_results else R.string.files_empty_title),
                            body = stringResource(if (current.filtered) R.string.no_results_hint else R.string.files_empty_body)
                        )
                        is FilesUiState.Error -> MessageState(
                            title = stringResource(R.string.files_error_title),
                            body = current.message ?: stringResource(R.string.error_unknown),
                            actionLabel = stringResource(R.string.retry),
                            onAction = viewModel::refresh
                        )
                        is FilesUiState.Content -> LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                                .padding(end = 24.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
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
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteRequest = null
                    if (request.fromSwipe) viewModel.scheduleDelete(request.ids) else viewModel.deleteNow(request.ids)
                    viewModel.clearSelection()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteRequest = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val detailItem = items.firstOrNull { it.id == detailId }
    if (detailItem != null) {
        ModalBottomSheet(onDismissRequest = { detailId = null }) {
            DetailsContent(detailItem)
        }
    }
}

@Composable
private fun DetailsContent(item: UploadItem) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(item.fileName, style = MaterialTheme.typography.titleLarge)
        DetailRow(stringResource(R.string.detail_direct_link), item.directUrl ?: stringResource(R.string.link_preparing), item.directUrl)
        DetailRow(stringResource(R.string.detail_item_page), item.itemUrl, item.itemUrl)
        DetailRow(stringResource(R.string.detail_size), Formatters.size(item.sizeBytes))
        DetailRow(stringResource(R.string.detail_mime), item.mimeType)
        DetailRow(stringResource(R.string.detail_date), formatDateTime(item.createdAt))
        DetailRow(stringResource(R.string.detail_identifier), item.identifier, item.identifier)
        if (item.status == UploadStatus.READY && item.directUrl != null) {
            Button(onClick = { UiActions.open(context, item.itemUrl) }) { Text(stringResource(R.string.open_item_page)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyValue: String? = null) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (copyValue != null) {
            IconButton(onClick = { UiActions.copy(context, copyValue) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy_value, label))
            }
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(56.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(6) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
        }
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
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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

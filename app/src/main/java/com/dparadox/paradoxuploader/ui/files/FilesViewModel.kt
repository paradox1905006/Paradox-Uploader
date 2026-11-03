package com.dparadox.paradoxuploader.ui.files

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.FileType
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.UiMessage
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.CleanupRepository
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder(@StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    NAME(R.string.sort_name),
    SIZE(R.string.sort_size)
}

/** Folder filter value for "files that are not in any folder". `null` means all folders. */
const val NO_FOLDER_FILTER = -1L

data class FileFilters(
    val query: String = "",
    val sort: SortOrder = SortOrder.NEWEST,
    val statuses: Set<UploadStatus> = emptySet(),
    val types: Set<FileType> = emptySet(),
    val folderId: Long? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || statuses.isNotEmpty() || types.isNotEmpty() || folderId != null
}

sealed interface FilesUiState {
    data object Loading : FilesUiState
    data class Empty(val filtered: Boolean) : FilesUiState
    data class Content(val items: List<UploadItem>) : FilesUiState
    data class Error(val message: String?) : FilesUiState
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: UploadRepository,
    private val folderRepository: FolderRepository,
    private val cleanup: CleanupRepository,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _filters = MutableStateFlow(FileFilters())
    val filters: StateFlow<FileFilters> = _filters.asStateFlow()

    private val hidden = MutableStateFlow<Set<Long>>(emptySet())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val messages = Channel<UiMessage>(Channel.BUFFERED)

    val folders: StateFlow<List<Folder>> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var pendingDelete: Pair<Set<Long>, Job>? = null

    val uiState: StateFlow<FilesUiState> = combine(repository.observeItems(), _filters, hidden) { items, filters, hiddenIds ->
        buildState(items, filters, hiddenIds)
    }
        .catch { emit(FilesUiState.Error(it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState.Loading)

    init {
        // A folder that no longer exists cannot stay selected as a filter.
        viewModelScope.launch {
            folderRepository.observeFolders().collect { list ->
                val selected = _filters.value.folderId
                if (selected != null && selected != NO_FOLDER_FILTER && list.none { it.id == selected }) {
                    _filters.update { it.copy(folderId = null) }
                }
            }
        }
    }

    private fun buildState(items: List<UploadItem>, filters: FileFilters, hiddenIds: Set<Long>): FilesUiState {
        val visible = items.filter { it.id !in hiddenIds }
        val filtered = visible
            .filter { filters.query.isBlank() || it.fileName.contains(filters.query.trim(), ignoreCase = true) }
            .filter { filters.statuses.isEmpty() || it.status in filters.statuses }
            .filter { filters.types.isEmpty() || it.fileType in filters.types }
            .filter {
                when (filters.folderId) {
                    null -> true
                    NO_FOLDER_FILTER -> it.folderId == null
                    else -> it.folderId == filters.folderId
                }
            }
        if (filtered.isEmpty()) return FilesUiState.Empty(filtered = filters.isActive && visible.isNotEmpty())
        val sorted = when (filters.sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.NAME -> filtered.sortedBy { it.fileName.lowercase() }
            SortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
        return FilesUiState.Content(sorted)
    }

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }
    fun setSort(sort: SortOrder) = _filters.update { it.copy(sort = sort) }
    fun setFolder(folderId: Long?) = _filters.update { it.copy(folderId = folderId) }

    fun toggleStatus(status: UploadStatus) = _filters.update {
        it.copy(statuses = if (status in it.statuses) it.statuses - status else it.statuses + status)
    }

    fun toggleType(type: FileType) = _filters.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun toggleSelection(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { _selection.value = emptySet() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val result = repository.refresh()
            _refreshing.value = false
            if (result is ApiResult.Failure) messages.send(UiMessage(R.string.refresh_failed))
        }
    }

    fun checkNow(id: Long) {
        viewModelScope.launch { repository.checkNow(id) }
    }

    /** Deletes right away (menu and bulk deletion). On failure the item stays and a Retry is offered. */
    fun deleteNow(ids: List<Long>) {
        appScope.launch { performDelete(ids) }
    }

    private suspend fun performDelete(ids: List<Long>) {
        for (id in ids) {
            val item = repository.getItem(id) ?: continue
            val name = item.fileName
            if (repository.delete(id) is ApiResult.Failure) {
                messages.send(
                    UiMessage(R.string.delete_failed, listOf(name), R.string.retry) { deleteNow(listOf(id)) }
                )
                continue
            }
            // The last file of a folder is gone: remove the (now empty) folder too.
            item.folderId?.let { folderRepository.deleteIfEmpty(it) }
            // archive.org keeps an item without files; remember it so it can be found and removed later.
            val wasOnArchive = item.status != UploadStatus.UPLOADING && item.status != UploadStatus.FAILED
            if (wasOnArchive && cleanup.onFileDeleted(item.identifier)) {
                messages.send(UiMessage(R.string.item_now_empty, listOf(name)))
            }
        }
    }

    /** Swipe-to-delete: hide the rows, offer Undo, and delete for real after a short delay. */
    fun scheduleDelete(ids: List<Long>) {
        commitPendingDelete()
        val idSet = ids.toSet()
        hidden.update { it + idSet }
        val job = appScope.launch {
            delay(UNDO_WINDOW_MS)
            pendingDelete = null
            performDelete(ids)
            hidden.update { it - idSet }
        }
        pendingDelete = idSet to job
        viewModelScope.launch {
            messages.send(UiMessage(R.string.deleting_file, emptyList(), R.string.undo) { undoDelete() })
        }
    }

    private fun undoDelete() {
        val (ids, job) = pendingDelete ?: return
        job.cancel()
        pendingDelete = null
        hidden.update { it - ids }
    }

    private fun commitPendingDelete() {
        val (ids, job) = pendingDelete ?: return
        job.cancel()
        pendingDelete = null
        appScope.launch {
            performDelete(ids.toList())
            hidden.update { it - ids }
        }
    }

    private companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}

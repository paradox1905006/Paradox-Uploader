package com.dparadox.paradoxuploader.ui.upload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.EnqueueResult
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.model.UiMessage
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadMeta
import com.dparadox.paradoxuploader.domain.model.UploadStats
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.SourceFiles
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.DuplicateChecker
import com.dparadox.paradoxuploader.domain.util.IncomingShares
import com.dparadox.paradoxuploader.domain.util.ProgressTracker
import com.dparadox.paradoxuploader.domain.util.UploadStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [duplicate] is true when a file with the same name and size was already uploaded before. */
data class PendingFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val duplicate: Boolean = false
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val repository: UploadRepository,
    private val sources: SourceFiles,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    tracker: ProgressTracker,
    private val shares: IncomingShares
) : ViewModel() {

    private val _pending = MutableStateFlow<List<PendingFile>>(emptyList())
    val pending: StateFlow<List<PendingFile>> = _pending.asStateFlow()

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var tags by mutableStateOf("")

    val progress = tracker.progress

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val folders: StateFlow<List<Folder>> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Destination folder for the next upload; null means "a separate link per file". */
    var selectedFolderId by mutableStateOf<Long?>(null)

    val messages = Channel<UiMessage>(Channel.BUFFERED)

    val recent: StateFlow<List<UploadItem>> = repository.observeItems()
        .map { it.take(30) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Counts over the whole history, not only the 30 entries shown in the queue. */
    val stats: StateFlow<UploadStats> = repository.observeItems()
        .map { UploadStatsCalculator.from(it, clock.nowMs()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UploadStats())

    init {
        // A folder that was removed (for example by the empty-folder cleanup) can no longer be selected.
        viewModelScope.launch {
            folderRepository.observeFolders().collect { list ->
                val selected = selectedFolderId
                if (selected != null && list.none { it.id == selected }) selectedFolderId = null
            }
        }
        // Files received through the share sheet end up in the pending list.
        viewModelScope.launch {
            shares.uris.collect { uris ->
                if (uris.isNotEmpty()) addUris(shares.consume())
            }
        }
    }

    fun addUris(uris: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.observeItems().first()
            val added = uris.mapNotNull { uri ->
                sources.describe(uri)?.let { info ->
                    val duplicate = DuplicateChecker.find(history, info.name, info.sizeBytes) != null
                    PendingFile(uri, info.name, info.sizeBytes, duplicate)
                }
            }
            val known = _pending.value.map { it.uri }.toSet()
            val fresh = added.filter { it.uri !in known }
            _pending.update { current -> (current + added).distinctBy { it.uri } }
            val duplicates = fresh.count { it.duplicate }
            if (duplicates > 0) messages.send(UiMessage(R.string.duplicates_found, listOf(duplicates)))
        }
    }

    /** Creates a folder (id == null) or changes an existing one, including its default metadata. */
    fun saveFolder(id: Long?, name: String, meta: FolderMeta) {
        viewModelScope.launch {
            if (id == null) {
                folderRepository.create(name, meta)?.let { selectedFolderId = it.id }
                return@launch
            }
            val before = folders.value.firstOrNull { it.id == id }
            val updated = folderRepository.update(id, name, meta) ?: return@launch
            if ((before?.fileCount ?: 0) == 0) {
                messages.send(UiMessage(R.string.folder_meta_saved))
                return@launch
            }
            // The item already exists on archive.org: send the new defaults there as well.
            when (val result = repository.pushFolderMetadata(updated.identifier, meta)) {
                is ApiResult.Success -> messages.send(UiMessage(R.string.folder_meta_applied))
                is ApiResult.Failure ->
                    if (result.kind != ErrorKind.NOT_FOUND) messages.send(UiMessage(R.string.folder_meta_push_failed))
            }
        }
    }

    fun removeEmptyFolders() {
        viewModelScope.launch { folderRepository.removeEmpty(0L) }
    }

    fun deleteFolder(id: Long) {
        if (selectedFolderId == id) selectedFolderId = null
        viewModelScope.launch { folderRepository.delete(id) }
    }

    /** A device folder was picked: queue every file in it and upload them into one archive folder. */
    fun addTree(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sources.retainTree(uri)
            val listing = sources.listTree(uri)
            if (listing == null || listing.files.isEmpty()) {
                messages.send(UiMessage(R.string.folder_empty))
                return@launch
            }
            addUris(listing.files)
            if (selectedFolderId == null) {
                val folder = folderRepository.findByName(listing.name) ?: folderRepository.create(listing.name)
                selectedFolderId = folder?.id
            }
            if (listing.truncated) messages.send(UiMessage(R.string.folder_truncated, listOf(listing.files.size)))
        }
    }

    fun removePending(uri: String) {
        _pending.update { list -> list.filterNot { it.uri == uri } }
    }

    /** "Skip duplicates": drops every file that was uploaded before from the pending list. */
    fun removeDuplicates() {
        _pending.update { list -> list.filterNot { it.duplicate } }
    }

    suspend fun startUpload(): EnqueueResult {
        // Files already uploaded (duplicates) are never sent again unless the user removes the warning first.
        val files = _pending.value.filterNot { it.duplicate }
        val folder = selectedFolderId?.let { folderRepository.get(it) }
        val result = repository.enqueue(
            files.map { it.uri },
            UploadMeta(title.ifBlank { null }, description.ifBlank { null }, tags.ifBlank { null }, folder)
        )
        if (result.error == null) {
            _pending.value = emptyList()
            title = ""
            description = ""
            tags = ""
        }
        return result
    }

    fun pauseAll() {
        viewModelScope.launch { repository.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { repository.resumeAll() }
    }

    fun cancel(id: Long) {
        viewModelScope.launch { repository.cancel(id) }
    }

    fun retry(id: Long) {
        viewModelScope.launch { repository.retry(id) }
    }

    fun checkNow(id: Long) {
        viewModelScope.launch { repository.checkNow(id) }
    }
}

package com.dparadox.paradoxuploader.ui.telegram

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.domain.telegram.ArchiveChannel
import com.dparadox.paradoxuploader.domain.telegram.ArchiveFile
import com.dparadox.paradoxuploader.domain.telegram.ArchiveLoad
import com.dparadox.paradoxuploader.domain.telegram.ArchiveRepository
import com.dparadox.paradoxuploader.domain.telegram.CreateArchiveChannel
import com.dparadox.paradoxuploader.domain.telegram.CreateDirectLink
import com.dparadox.paradoxuploader.domain.telegram.DirectLink
import com.dparadox.paradoxuploader.domain.telegram.DownloadProgress
import com.dparadox.paradoxuploader.domain.telegram.DownloadTelegramFile
import com.dparadox.paradoxuploader.domain.telegram.LoadArchiveFiles
import com.dparadox.paradoxuploader.domain.telegram.ObserveArchiveChannels
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.toTgError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------- Channels

data class ChannelsUi(val busy: Boolean = false, val error: TgError? = null)

@HiltViewModel
class ArchiveChannelsViewModel @Inject constructor(
    observe: ObserveArchiveChannels,
    private val create: CreateArchiveChannel,
    private val repo: ArchiveRepository
) : ViewModel() {

    val channels: StateFlow<List<ArchiveChannel>> =
        observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ChannelsUi())
    val ui: StateFlow<ChannelsUi> = _ui

    /** Calls [onCreated] with the new channel on success. */
    fun createChannel(
        title: String,
        description: String,
        isPublic: Boolean,
        username: String,
        onCreated: (ArchiveChannel) -> Unit
    ) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, error = null) }
        when (val r = create(title, description, isPublic, username.takeIf { it.isNotBlank() })) {
            is TgResult.Success -> {
                _ui.update { it.copy(busy = false) }
                onCreated(r.value)
            }
            is TgResult.Failure -> _ui.update { it.copy(busy = false, error = r.error) }
        }
    }

    fun setDefault(chatId: Long) = viewModelScope.launch { repo.setDefault(chatId) }
    fun removeReference(chatId: Long) = viewModelScope.launch { repo.removeReference(chatId) }
    fun dismissError() = _ui.update { it.copy(error = null) }
}

// ---------------------------------------------------------------- File explorer

data class ExplorerUi(
    val loading: Boolean = true,
    val scanned: Int = 0,
    val files: List<ArchiveFile> = emptyList(),
    val error: TgError? = null,
    val selected: Set<Long> = emptySet(),
    /** Status line while downloading or generating links, e.g. "Downloading 2/5". */
    val busy: String? = null,
    val message: String? = null
)

@HiltViewModel
class ArchiveExplorerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val load: LoadArchiveFiles,
    private val download: DownloadTelegramFile,
    private val createLink: CreateDirectLink
) : ViewModel() {

    private val chatId: Long = checkNotNull(savedState.get<Long>("chatId"))

    private val _ui = MutableStateFlow(ExplorerUi())
    val ui: StateFlow<ExplorerUi> = _ui

    private val _links = MutableStateFlow<List<DirectLink>>(emptyList())
    val links: StateFlow<List<DirectLink>> = _links

    private var loadJob: Job? = null

    init { reload() }

    /** Walks the full channel history. The list fills in page by page. */
    fun reload() {
        loadJob?.cancel()
        _ui.update { ExplorerUi(loading = true) }
        loadJob = viewModelScope.launch {
            load(chatId).collect { step ->
                when (step) {
                    is ArchiveLoad.Loading -> _ui.update {
                        it.copy(scanned = step.scanned, files = step.files)
                    }
                    is ArchiveLoad.Done -> _ui.update {
                        it.copy(loading = false, scanned = step.scanned, files = step.files)
                    }
                    is ArchiveLoad.Failed -> _ui.update {
                        it.copy(loading = false, error = step.error)
                    }
                }
            }
        }
    }

    fun toggle(key: Long) = _ui.update {
        val next = if (key in it.selected) it.selected - key else it.selected + key
        it.copy(selected = next)
    }

    /** Selects every downloadable file in the list. */
    fun selectAll() = _ui.update { s ->
        s.copy(selected = s.files.filter { it.canSave }.map { it.key }.toSet())
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }
    fun dismissError() = _ui.update { it.copy(error = null) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }
    fun dismissLinks() { _links.value = emptyList() }

    private fun selectedFiles(): List<ArchiveFile> {
        val s = _ui.value
        return s.files.filter { it.key in s.selected && it.canSave }
    }

    /** Downloads the selected files one at a time straight from Telegram into Downloads/Telegram. */
    fun downloadSelected() = viewModelScope.launch {
        val items = selectedFiles()
        var ok = 0
        for ((i, f) in items.withIndex()) {
            _ui.update { it.copy(busy = "Downloading ${i + 1}/${items.size}: ${f.fileName}") }
            try {
                download(f.toResolved()).collect { p ->
                    if (p is DownloadProgress.Running && p.total > 0) {
                        val pct = (p.bytes * 100 / p.total).toInt()
                        _ui.update { it.copy(busy = "Downloading ${i + 1}/${items.size} · $pct%") }
                    }
                }
                ok++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(error = e.toTgError()) }
            }
        }
        _ui.update {
            it.copy(busy = null, message = "Downloaded $ok of ${items.size} to Downloads/Telegram")
        }
    }

    /** Creates a temporary link for every selected file. Links expire after the TTL in Settings. */
    fun generateLinks() = viewModelScope.launch {
        val items = selectedFiles()
        val out = mutableListOf<DirectLink>()
        for ((i, f) in items.withIndex()) {
            _ui.update { it.copy(busy = "Creating link ${i + 1}/${items.size}") }
            when (val r = createLink(f.toResolved())) {
                is TgResult.Success -> out += r.value
                is TgResult.Failure -> _ui.update { it.copy(error = r.error) }
            }
        }
        _ui.update { it.copy(busy = null) }
        _links.value = out
    }
}

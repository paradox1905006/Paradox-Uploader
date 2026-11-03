package com.dparadox.paradoxuploader.ui.telegram

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.ForumTopic
import com.dparadox.paradoxuploader.domain.telegram.PickedFile
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.UploadController
import com.dparadox.paradoxuploader.domain.telegram.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DestinationsUi(
    val loading: Boolean = true,
    val items: List<ChatDestination> = emptyList(),
    val busy: Boolean = false,
    val error: TgError? = null
)

data class TopicsUi(
    val loading: Boolean = false,
    val items: List<ForumTopic> = emptyList(),
    val busy: Boolean = false,
    val error: TgError? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: UploadRepository,
    private val controller: UploadController
) : ViewModel() {

    val selection = controller.selection
    val recent = controller.recent
    val queue = controller.jobs
    val paused = controller.paused

    private val _destinations = MutableStateFlow(DestinationsUi())
    val destinations: StateFlow<DestinationsUi> = _destinations

    private val _topics = MutableStateFlow(TopicsUi())
    val topics: StateFlow<TopicsUi> = _topics

    private val _files = MutableStateFlow<List<PickedFile>>(emptyList())
    val files: StateFlow<List<PickedFile>> = _files

    init { loadDestinations() }

    // ------------------------------------------------------------ Destinations

    fun loadDestinations() = viewModelScope.launch {
        _destinations.update { it.copy(loading = true, error = null) }
        when (val r = repo.destinations()) {
            is TgResult.Success -> _destinations.update { it.copy(loading = false, items = r.value) }
            is TgResult.Failure -> _destinations.update { it.copy(loading = false, error = r.error) }
        }
    }

    fun pickDestination(dest: ChatDestination) = controller.selectDestination(dest)

    fun createChannel(title: String, description: String, isPublic: Boolean, username: String, onDone: () -> Unit) =
        viewModelScope.launch {
            _destinations.update { it.copy(busy = true, error = null) }
            when (val r = repo.createChannel(title.trim(), description.trim(), isPublic, username.takeIf { it.isNotBlank() })) {
                is TgResult.Success -> {
                    controller.selectDestination(r.value)
                    _destinations.update { it.copy(busy = false, items = listOf(r.value) + it.items) }
                    onDone()
                }
                is TgResult.Failure -> _destinations.update { it.copy(busy = false, error = r.error) }
            }
        }

    fun createGroup(title: String, description: String, forum: Boolean, onDone: () -> Unit) =
        viewModelScope.launch {
            _destinations.update { it.copy(busy = true, error = null) }
            when (val r = repo.createGroup(title.trim(), description.trim(), forum)) {
                is TgResult.Success -> {
                    controller.selectDestination(r.value)
                    _destinations.update { it.copy(busy = false, items = listOf(r.value) + it.items) }
                    onDone()
                }
                is TgResult.Failure -> _destinations.update { it.copy(busy = false, error = r.error) }
            }
        }

    fun dismissDestinationError() = _destinations.update { it.copy(error = null) }

    // ------------------------------------------------------------ Topics

    fun loadTopics(chatId: Long) = viewModelScope.launch {
        _topics.update { it.copy(loading = true, error = null) }
        when (val r = repo.topics(chatId)) {
            is TgResult.Success -> _topics.update { it.copy(loading = false, items = r.value) }
            is TgResult.Failure -> _topics.update { it.copy(loading = false, error = r.error) }
        }
    }

    fun createTopic(name: String, color: Int) {
        val dest = selection.value.destination ?: return
        viewModelScope.launch {
            _topics.update { it.copy(busy = true, error = null) }
            when (val r = repo.createTopic(dest.chatId, name.trim(), color)) {
                is TgResult.Success -> {
                    controller.selectTopic(r.value)
                    _topics.update { it.copy(busy = false, items = it.items + r.value) }
                }
                is TgResult.Failure -> _topics.update { it.copy(busy = false, error = r.error) }
            }
        }
    }

    fun selectTopic(topic: ForumTopic?) = controller.selectTopic(topic)
    fun dismissTopicError() = _topics.update { it.copy(error = null) }

    // ------------------------------------------------------------ Files

    fun addFiles(uris: List<Uri>) = viewModelScope.launch {
        val picked = withContext(Dispatchers.IO) { uris.mapNotNull { readPicked(it) } }
        _files.update { current -> current + picked.filterNot { p -> current.any { it.uri == p.uri } } }
    }

    fun removeFile(uri: String) = _files.update { list -> list.filterNot { it.uri == uri } }
    fun clearFiles() = _files.update { emptyList() }

    /** Queues the picked files with the composed caption. Files are cleared once queued. */
    fun startUploads(text: String, tags: String, footer: Boolean) {
        val files = _files.value
        if (files.isEmpty() || selection.value.destination == null) return
        controller.enqueue(files, composeCaption(text, tags, footer))
        _files.value = emptyList()
    }

    private fun readPicked(uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return PickedFile(uri.toString(), name, mime, size)
    }

    // ------------------------------------------------------------ Queue

    fun setPaused(paused: Boolean) = controller.setPaused(paused)
    fun retry(id: String) = controller.retry(id)
    fun cancel(id: String) = controller.cancel(id)
    fun clearFinished() = controller.clearFinished()

    companion object {
        /** Joins the user's text, tags and an optional footer, respecting Telegram's 1024-char caption limit. */
        fun composeCaption(text: String, tags: String, footer: Boolean): String {
            val tagLine = tags.split(',', ' ', '\n')
                .map { it.trim().trimStart('#') }
                .filter { it.isNotEmpty() }
                .joinToString(" ") { "#$it" }
            return listOf(text.trim(), tagLine, if (footer) "Uploaded via Paradox Uploader" else "")
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
                .take(1024)
        }
    }
}

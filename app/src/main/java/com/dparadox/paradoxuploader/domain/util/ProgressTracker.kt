package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Live (in-memory) upload progress per upload id. */
@Singleton
class ProgressTracker @Inject constructor() {
    private val _progress = MutableStateFlow<Map<Long, UploadProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, UploadProgress>> = _progress.asStateFlow()

    fun update(id: Long, progress: UploadProgress) = _progress.update { it + (id to progress) }
    fun clear(id: Long) = _progress.update { it - id }
}

/** Uris received through the Android share sheet, waiting to be picked up by the upload screen. */
@Singleton
class IncomingShares @Inject constructor() {
    private val _uris = MutableStateFlow<List<String>>(emptyList())
    val uris: StateFlow<List<String>> = _uris.asStateFlow()

    fun push(list: List<String>) = _uris.update { it + list }
    fun consume(): List<String> = _uris.getAndUpdate { emptyList() }
}

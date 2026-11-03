package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory hand-over between the Resolver, File Details and Download Link screens. */
@Singleton
class ResolverSession @Inject constructor() {
    private val _file = MutableStateFlow<ResolvedFile?>(null)
    private val _link = MutableStateFlow<DirectLink?>(null)
    val file: StateFlow<ResolvedFile?> = _file
    val link: StateFlow<DirectLink?> = _link

    fun setFile(file: ResolvedFile) {
        _file.value = file
        _link.value = null
    }

    fun setLink(link: DirectLink?) {
        _link.value = link
    }

    fun clear() {
        _file.value = null
        _link.value = null
    }
}

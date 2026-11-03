package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-app messages (snackbars), used when notifications are unavailable. */
@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    fun post(message: UiMessage) {
        _events.tryEmit(message)
    }
}

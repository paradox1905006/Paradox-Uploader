package com.dparadox.paradoxuploader.ui.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.data.telegram.HomeTab
import com.dparadox.paradoxuploader.data.telegram.TabPrefsStore
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val busy: Boolean = false,
    val results: List<ChatDestination> = emptyList(),
    val error: TgError? = null
)

@HiltViewModel
class TelegramHomeViewModel @Inject constructor(
    private val prefs: TabPrefsStore,
    private val repo: UploadRepository
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.UPLOAD)
    val tab: StateFlow<HomeTab> = _tab

    val uploadDefault: StateFlow<ChatDestination?> =
        prefs.defaultOf(HomeTab.UPLOAD).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val downloadDefault: StateFlow<ChatDestination?> =
        prefs.defaultOf(HomeTab.DOWNLOAD).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val uploadPins: StateFlow<List<ChatDestination>> =
        prefs.pinsOf(HomeTab.UPLOAD).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloadPins: StateFlow<List<ChatDestination>> =
        prefs.pinsOf(HomeTab.DOWNLOAD).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search

    private var searchJob: Job? = null

    fun selectTab(tab: HomeTab) { _tab.value = tab }

    /** Searches the chats and channels this account can see. Waits briefly while typing. */
    fun query(text: String) {
        _search.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (text.isBlank()) {
                _search.update { it.copy(busy = false, results = emptyList(), error = null) }
                return@launch
            }
            delay(350)
            _search.update { it.copy(busy = true, error = null) }
            when (val r = repo.searchChats(text.trim())) {
                is TgResult.Success -> _search.update { it.copy(busy = false, results = r.value) }
                is TgResult.Failure -> _search.update { it.copy(busy = false, error = r.error) }
            }
        }
    }

    fun setDefault(tab: HomeTab, dest: ChatDestination) {
        viewModelScope.launch { prefs.setDefault(tab, dest) }
    }

    fun togglePin(tab: HomeTab, dest: ChatDestination) {
        viewModelScope.launch { prefs.togglePin(tab, dest) }
    }
}

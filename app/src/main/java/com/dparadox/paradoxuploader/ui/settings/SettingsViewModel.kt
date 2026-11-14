package com.dparadox.paradoxuploader.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.CleanupScan
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.HistoryResult
import com.dparadox.paradoxuploader.domain.model.KeyTestResult
import com.dparadox.paradoxuploader.domain.model.UpdateCheckResult
import com.dparadox.paradoxuploader.domain.model.UpdateInfo
import com.dparadox.paradoxuploader.domain.repository.CleanupRepository
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.repository.HistoryRepository
import com.dparadox.paradoxuploader.domain.repository.KeyStore
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.util.IdentifierGenerator
import com.dparadox.paradoxuploader.domain.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyUiState {
    data object Idle : KeyUiState
    data object Testing : KeyUiState
    data class TestPassed(val cleanedUp: Boolean) : KeyUiState
    data class TestFailed(val kind: ErrorKind, val message: String) : KeyUiState
    data class AskSaveAnyway(val kind: ErrorKind, val message: String) : KeyUiState
    data object Saved : KeyUiState
    data object Removed : KeyUiState
    data object MissingInput : KeyUiState
}

enum class HistoryAction { EXPORT, IMPORT, SYNC_FILE, SYNC_ARCHIVE, ADD_ITEM }

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Failed(val reason: String? = null) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val keyStore: KeyStore,
    private val uploads: UploadRepository,
    private val history: HistoryRepository,
    private val folders: FolderRepository,
    private val cleanup: CleanupRepository,
    private val updates: UpdateChecker
) : ViewModel() {

    var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    fun checkForUpdates() {
        if (updateState is UpdateUiState.Checking) return
        viewModelScope.launch {
            updateState = UpdateUiState.Checking
            updateState = when (val result = updates.check(BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                is UpdateCheckResult.Failed -> UpdateUiState.Failed(result.reason)
            }
        }
    }

    /** Changes the Wi-Fi rule and applies it to files that are already queued. */
    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(wifiOnly = value) }
            uploads.applyUploadPolicy()
        }
    }

    /** "Upload only while charging" - also applied to files that are already queued. */
    fun setChargingOnly(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(chargingOnly = value) }
            uploads.applyUploadPolicy()
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val saved = keyStore.get()
    var accessKey by mutableStateOf(saved?.accessKey.orEmpty())
    var secretKey by mutableStateOf(saved?.secretKey.orEmpty())
    var showKeys by mutableStateOf(false)
    var hasSavedKeys by mutableStateOf(saved != null)
        private set
    var keyState by mutableStateOf<KeyUiState>(KeyUiState.Idle)
        private set

    val emptyFolderCount: StateFlow<Int> = folders.observeFolders()
        .map { list -> list.count { it.fileCount == 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var removedFolders by mutableStateOf<Int?>(null)
        private set
    var scanBusy by mutableStateOf(false)
        private set
    var scanResult by mutableStateOf<CleanupScan?>(null)
        private set

    fun removeEmptyFolders() {
        viewModelScope.launch { removedFolders = folders.removeEmpty(0L) }
    }

    fun scanEmptyItems() {
        if (scanBusy) return
        viewModelScope.launch {
            scanBusy = true
            scanResult = cleanup.scanEmptyItems()
            scanBusy = false
        }
    }

    fun dismissEmptyItem(identifier: String) {
        viewModelScope.launch {
            cleanup.dismiss(identifier)
            scanResult = scanResult?.let { it.copy(empty = it.empty - identifier) }
        }
    }

    var historyBusy by mutableStateOf(false)
        private set
    var historyResult by mutableStateOf<Pair<HistoryAction, HistoryResult>?>(null)
        private set
    var archiveInput by mutableStateOf("")

    private fun runHistory(action: HistoryAction, block: suspend () -> HistoryResult) {
        if (historyBusy) return
        viewModelScope.launch {
            historyBusy = true
            historyResult = null
            val result = block()
            historyResult = action to result
            historyBusy = false
        }
    }

    fun exportHistory(uri: String) = runHistory(HistoryAction.EXPORT) { history.exportToDocument(uri) }

    fun importHistory(uri: String) = runHistory(HistoryAction.IMPORT) { history.importFromDocument(uri) }

    /** Remembers the sync file and runs the first sync right away. */
    fun chooseSyncFile(uri: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(syncUri = uri, lastSyncAt = 0L) } }
        runHistory(HistoryAction.SYNC_FILE) { history.syncWithDocument(uri) }
    }

    fun syncNow() {
        val uri = settings.value.syncUri
        if (uri.isBlank()) return
        runHistory(HistoryAction.SYNC_FILE) { history.syncWithDocument(uri) }
    }

    fun stopSync() {
        viewModelScope.launch { settingsRepository.update { it.copy(syncUri = "", lastSyncAt = 0L) } }
    }

    fun syncWithArchive() = runHistory(HistoryAction.SYNC_ARCHIVE) { history.syncWithArchive() }

    fun addFromArchive() {
        val input = archiveInput
        if (input.isBlank()) return
        runHistory(HistoryAction.ADD_ITEM) {
            val result = history.addFromArchive(input)
            if (result is HistoryResult.Success) archiveInput = ""
            result
        }
    }

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(block) }
    }

    private fun credentialsOrNull(): Credentials? {
        val access = accessKey.trim()
        val secret = secretKey.trim()
        return if (access.isEmpty() || secret.isEmpty()) null else Credentials(access, secret)
    }

    /** Uploads a tiny file to a temporary item and deletes it again, without saving anything. */
    fun testKeys() {
        val credentials = credentialsOrNull() ?: run {
            keyState = KeyUiState.MissingInput
            return
        }
        viewModelScope.launch {
            keyState = KeyUiState.Testing
            keyState = when (val result = uploads.testKeys(credentials)) {
                is KeyTestResult.Success -> {
                    cleanup.markPossiblyEmpty(IdentifierGenerator.testItemFor(credentials.accessKey))
                    KeyUiState.TestPassed(result.cleanedUp)
                }
                is KeyTestResult.Failure -> KeyUiState.TestFailed(result.kind, result.message)
            }
        }
    }

    /** Saves the keys only when the test passes; otherwise the user may choose "Save anyway". */
    fun saveKeys() {
        val credentials = credentialsOrNull() ?: run {
            keyState = KeyUiState.MissingInput
            return
        }
        viewModelScope.launch {
            keyState = KeyUiState.Testing
            keyState = when (val result = uploads.testKeys(credentials)) {
                is KeyTestResult.Success -> {
                    cleanup.markPossiblyEmpty(IdentifierGenerator.testItemFor(credentials.accessKey))
                    persist(credentials)
                    KeyUiState.Saved
                }
                is KeyTestResult.Failure -> KeyUiState.AskSaveAnyway(result.kind, result.message)
            }
        }
    }

    fun saveAnyway() {
        val credentials = credentialsOrNull() ?: return
        persist(credentials)
        keyState = KeyUiState.Saved
    }

    fun dismissKeyDialog() {
        keyState = KeyUiState.Idle
    }

    fun removeKeys() {
        keyStore.clear()
        accessKey = ""
        secretKey = ""
        hasSavedKeys = false
        keyState = KeyUiState.Removed
    }

    private fun persist(credentials: Credentials) {
        keyStore.save(credentials)
        hasSavedKeys = true
    }
}

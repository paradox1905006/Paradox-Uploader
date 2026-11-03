package com.dparadox.paradoxuploader.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.UiMessage
import com.dparadox.paradoxuploader.domain.model.UpdateInfo
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.util.AppEventBus
import com.dparadox.paradoxuploader.domain.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Set when a notification asks to open My Files, optionally scrolled to one file. */
data class DeepLink(val fileId: Long?)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker,
    bus: AppEventBus
) : ViewModel() {

    /** A newer version found by the automatic check; null when there is nothing to show. */
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    init {
        viewModelScope.launch { _update.value = updateChecker.checkIfDue(BuildConfig.VERSION_NAME) }
    }

    /** "Later": hides the dialog and does not ask again for this version. */
    fun dismissUpdate() {
        val info = _update.value ?: return
        _update.value = null
        viewModelScope.launch { updateChecker.dismiss(info.versionName) }
    }

    /** `null` until the first value was read from DataStore. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val busEvents: SharedFlow<UiMessage> = bus.events

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }

    fun acceptWarning() {
        viewModelScope.launch { settingsRepository.update { it.copy(warningAccepted = true) } }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.update { it.copy(onboardingDone = true) } }
    }
}

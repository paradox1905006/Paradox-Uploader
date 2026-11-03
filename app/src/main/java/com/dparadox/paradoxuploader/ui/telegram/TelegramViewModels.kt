package com.dparadox.paradoxuploader.ui.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dparadox.paradoxuploader.data.telegram.TelegramPrefsImpl
import com.dparadox.paradoxuploader.domain.telegram.ApiCredentials
import com.dparadox.paradoxuploader.domain.telegram.CreateDirectLink
import com.dparadox.paradoxuploader.domain.telegram.DirectLink
import com.dparadox.paradoxuploader.domain.telegram.DownloadProgress
import com.dparadox.paradoxuploader.domain.telegram.DownloadTelegramFile
import com.dparadox.paradoxuploader.domain.telegram.LogoutFromTelegram
import com.dparadox.paradoxuploader.domain.telegram.ResolveTelegramLink
import com.dparadox.paradoxuploader.domain.telegram.ResolverSession
import com.dparadox.paradoxuploader.domain.telegram.RevokeDirectLink
import com.dparadox.paradoxuploader.domain.telegram.SignInWithPhone
import com.dparadox.paradoxuploader.domain.telegram.SubmitLoginCode
import com.dparadox.paradoxuploader.domain.telegram.SubmitTwoFactorPassword
import com.dparadox.paradoxuploader.domain.telegram.TelegramAuthRepository
import com.dparadox.paradoxuploader.domain.telegram.TelegramPrefs
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.toTgError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------- Auth

data class AuthUi(val busy: Boolean = false, val error: TgError? = null)

@HiltViewModel
class TelegramAuthViewModel @Inject constructor(
    private val repo: TelegramAuthRepository,
    private val signIn: SignInWithPhone,
    private val submitCode: SubmitLoginCode,
    private val submitPassword: SubmitTwoFactorPassword
) : ViewModel() {

    val authState = repo.authState
    val connection = repo.connection

    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui

    init { repo.start() }

    fun hasApiCredentials(): Boolean = repo.apiCredentials() != null

    fun saveApi(apiId: String, apiHash: String) = launchOp {
        repo.saveApiCredentials(ApiCredentials(apiId.trim().toIntOrNull() ?: 0, apiHash.trim()))
    }

    fun phone(phone: String) = launchOp { signIn(phone) }
    fun code(code: String) = launchOp { submitCode(code) }
    fun password(password: String) = launchOp { submitPassword(password) }

    /** Goes back to the phone step (e.g. wrong number): logs out the pending login. */
    fun useDifferentNumber() = launchOp { repo.logout() }

    fun dismissError() { _ui.value = _ui.value.copy(error = null) }

    private fun launchOp(block: suspend () -> TgResult<Unit>) {
        if (_ui.value.busy) return
        _ui.value = AuthUi(busy = true)
        viewModelScope.launch {
            val result = block()
            _ui.value = AuthUi(busy = false, error = (result as? TgResult.Failure)?.error)
        }
    }
}

// ---------------------------------------------------------------- Resolver

sealed interface ResolveUi {
    data object Idle : ResolveUi
    data object Loading : ResolveUi
    data class Error(val error: TgError) : ResolveUi
}

@HiltViewModel
class ResolverViewModel @Inject constructor(
    private val resolveLink: ResolveTelegramLink,
    repo: TelegramAuthRepository
) : ViewModel() {

    val authState = repo.authState
    val connection = repo.connection

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _ui = MutableStateFlow<ResolveUi>(ResolveUi.Idle)
    val ui: StateFlow<ResolveUi> = _ui

    private val _opened = Channel<Unit>(Channel.BUFFERED)
    val opened: Flow<Unit> = _opened.receiveAsFlow()

    fun setInput(value: String) {
        _input.value = value
        if (_ui.value is ResolveUi.Error) _ui.value = ResolveUi.Idle
    }

    fun resolve() {
        if (_ui.value == ResolveUi.Loading) return
        _ui.value = ResolveUi.Loading
        viewModelScope.launch {
            when (val r = resolveLink(_input.value)) {
                is TgResult.Success -> {
                    _ui.value = ResolveUi.Idle
                    _opened.send(Unit)
                }
                is TgResult.Failure -> _ui.value = ResolveUi.Error(r.error)
            }
        }
    }
}

// ---------------------------------------------------------------- File details + download link

sealed interface LinkUi {
    data object Idle : LinkUi
    data object Creating : LinkUi
    data class Failed(val error: TgError) : LinkUi
}

sealed interface DownloadUi {
    data object Idle : DownloadUi
    data class Running(val bytes: Long, val total: Long) : DownloadUi
    data class Done(val uri: String, val name: String) : DownloadUi
    data class Failed(val error: TgError) : DownloadUi
}

enum class LinkAction { COPY, SHARE }

@HiltViewModel
class FileActionsViewModel @Inject constructor(
    private val session: ResolverSession,
    private val createLink: CreateDirectLink,
    private val revokeLink: RevokeDirectLink,
    private val downloadFile: DownloadTelegramFile
) : ViewModel() {

    val file = session.file
    val link = session.link

    private val _linkUi = MutableStateFlow<LinkUi>(LinkUi.Idle)
    val linkUi: StateFlow<LinkUi> = _linkUi

    private val _download = MutableStateFlow<DownloadUi>(DownloadUi.Idle)
    val download: StateFlow<DownloadUi> = _download

    private val _linkEvents = Channel<Pair<DirectLink, LinkAction>>(Channel.BUFFERED)
    val linkEvents: Flow<Pair<DirectLink, LinkAction>> = _linkEvents.receiveAsFlow()

    private var downloadJob: Job? = null

    /** Milliseconds left on the current link, ticking once a second. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val remainingMs: StateFlow<Long?> = session.link.flatMapLatest { l ->
        if (l == null) emptyFlow() else flow {
            while (true) {
                emit((l.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                delay(1000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Creates a link unless a valid one already exists. */
    fun ensureLink(action: LinkAction? = null) {
        val existing = session.link.value
        if (existing != null && existing.expiresAt > System.currentTimeMillis()) {
            if (action != null) viewModelScope.launch { _linkEvents.send(existing to action) }
            return
        }
        val f = session.file.value ?: return
        if (_linkUi.value == LinkUi.Creating) return
        _linkUi.value = LinkUi.Creating
        viewModelScope.launch {
            when (val r = createLink(f)) {
                is TgResult.Success -> {
                    _linkUi.value = LinkUi.Idle
                    if (action != null) _linkEvents.send(r.value to action)
                }
                is TgResult.Failure -> _linkUi.value = LinkUi.Failed(r.error)
            }
        }
    }

    fun revoke() {
        session.link.value?.let(revokeLink::invoke)
    }

    fun startDownload() {
        val f = session.file.value ?: return
        if (_download.value is DownloadUi.Running) return
        _download.value = DownloadUi.Running(0, f.sizeBytes)
        downloadJob = viewModelScope.launch {
            downloadFile(f)
                .catch { _download.value = DownloadUi.Failed(it.toTgError()) }
                .collect { p ->
                    _download.value = when (p) {
                        is DownloadProgress.Running -> DownloadUi.Running(p.bytes, p.total)
                        is DownloadProgress.Done -> DownloadUi.Done(p.uri, p.fileName)
                    }
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _download.value = DownloadUi.Idle
    }
}

// ---------------------------------------------------------------- Settings

@HiltViewModel
class TelegramSettingsViewModel @Inject constructor(
    private val repo: TelegramAuthRepository,
    private val prefs: TelegramPrefs,
    private val logoutUseCase: LogoutFromTelegram
) : ViewModel() {

    val authState = repo.authState
    val ttl: StateFlow<Int> = prefs.linkTtlMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, TelegramPrefsImpl.DEFAULT_TTL)
    val ttlOptions = TelegramPrefsImpl.OPTIONS

    private val _message = Channel<TgError?>(Channel.BUFFERED)
    val cacheCleared: Flow<TgError?> = _message.receiveAsFlow()

    fun setTtl(minutes: Int) { viewModelScope.launch { prefs.setLinkTtlMinutes(minutes) } }

    fun logout() { viewModelScope.launch { logoutUseCase() } }

    fun clearCache() {
        viewModelScope.launch { _message.send((repo.clearCache() as? TgResult.Failure)?.error) }
    }

    fun saveApi(apiId: String, apiHash: String, onResult: (TgError?) -> Unit) {
        viewModelScope.launch {
            val r = repo.saveApiCredentials(ApiCredentials(apiId.trim().toIntOrNull() ?: 0, apiHash.trim()))
            onResult((r as? TgResult.Failure)?.error)
        }
    }
}

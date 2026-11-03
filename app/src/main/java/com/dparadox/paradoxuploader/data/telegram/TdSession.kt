package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import android.os.Build
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.telegram.Account
import com.dparadox.paradoxuploader.domain.telegram.ApiCredentials
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.domain.telegram.ConnectionStatus
import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TgError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the single TDLib client (Telegram's official MTProto implementation) for the app.
 * Session data is stored in TDLib's database, encrypted with a key kept in [TelegramVault].
 */
@Singleton
class TdSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: TelegramVault,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val lock = Any()
    @Volatile private var client: Client? = null

    private val _auth = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _auth

    private val _connection = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connection: StateFlow<ConnectionStatus> = _connection

    /** File transfer progress. Subscribe before the transfer starts. */
    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(
        extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val fileUpdates: SharedFlow<TdApi.UpdateFile> = _fileUpdates

    /** Send results. Replayed so a late subscriber still sees a result that already arrived. */
    private val _sendUpdates = MutableSharedFlow<TdApi.Object>(
        replay = 64, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sendUpdates: SharedFlow<TdApi.Object> = _sendUpdates

    @Volatile private var account: Account? = null

    fun ensureStarted() {
        synchronized(lock) {
            if (client != null) return
            if (vault.apiCredentials() == null) {
                _auth.value = AuthState.NeedsApiCredentials
                return
            }
            runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
            client = Client.create(
                { update -> onUpdate(update) },
                { /* update handler exception: never log payloads */ },
                { /* default exception handler */ }
            )
        }
    }

    /** Re-reads the API credentials: closes the current client; [onClosed] starts a fresh one. */
    fun restart() {
        val c = client
        if (c == null) ensureStarted() else c.send(TdApi.Close()) { }
    }

    suspend fun <R : TdApi.Object> send(function: TdApi.Function<R>): R =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resumeWithException(TelegramException(TgError.NotLoggedIn))
                return@suspendCancellableCoroutine
            }
            c.send(function) { result ->
                when (result) {
                    is TdApi.Error -> cont.resumeWithException(TdErrors.map(result))
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        cont.resume(result as R)
                    }
                }
            }
        }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update)
            is TdApi.UpdateMessageSendSucceeded, is TdApi.UpdateMessageSendFailed -> _sendUpdates.tryEmit(update)
            is TdApi.UpdateAuthorizationState -> onAuth(update.authorizationState)
            is TdApi.UpdateConnectionState -> _connection.value = when (update.state) {
                is TdApi.ConnectionStateReady -> ConnectionStatus.CONNECTED
                is TdApi.ConnectionStateWaitingForNetwork -> ConnectionStatus.WAITING_FOR_NETWORK
                else -> ConnectionStatus.CONNECTING
            }
        }
    }

    private fun onAuth(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = AuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = AuthState.WaitCode(state.codeInfo.phoneNumber.orEmpty())
            is TdApi.AuthorizationStateWaitPassword -> _auth.value = AuthState.WaitPassword(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateReady -> {
                _auth.value = AuthState.Ready(account)
                scope.launch { loadAccountAndChats() }
            }
            is TdApi.AuthorizationStateLoggingOut -> _auth.value = AuthState.LoggingOut
            is TdApi.AuthorizationStateClosing -> Unit
            is TdApi.AuthorizationStateClosed -> {
                synchronized(lock) { client = null }
                account = null
                _auth.value = AuthState.Initializing
                scope.launch { ensureStarted() }
            }
            else -> _auth.value = AuthState.Unsupported(state.javaClass.simpleName.removePrefix("AuthorizationState"))
        }
    }

    private fun sendParameters() {
        synchronized(lock) {
            val c = client ?: return
            val creds: ApiCredentials = vault.apiCredentials() ?: run {
                _auth.value = AuthState.NeedsApiCredentials
                return
            }
            val params = TdApi.SetTdlibParameters().apply {
                useTestDc = false
                databaseDirectory = File(context.noBackupFilesDir, "tdlib/db").apply { mkdirs() }.absolutePath
                filesDirectory = File(context.cacheDir, "tdlib/files").apply { mkdirs() }.absolutePath
                databaseEncryptionKey = vault.databaseKey()
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = false
                useSecretChats = false
                apiId = creds.apiId
                apiHash = creds.apiHash
                systemLanguageCode = Locale.getDefault().language.ifBlank { "en" }
                deviceModel = Build.MODEL.orEmpty().take(40)
                systemVersion = Build.VERSION.RELEASE.orEmpty()
                applicationVersion = BuildConfig.VERSION_NAME
            }
            c.send(params) { result ->
                if (result is TdApi.Error) _auth.value = AuthState.Unsupported("Parameters: ${result.code}")
            }
        }
    }

    private suspend fun loadAccountAndChats() {
        runCatching {
            val me = send(TdApi.GetMe())
            val name = listOf(me.firstName, me.lastName).filter { !it.isNullOrBlank() }.joinToString(" ")
            account = Account(
                displayName = name.ifBlank { me.usernames?.activeUsernames?.firstOrNull().orEmpty() },
                username = me.usernames?.activeUsernames?.firstOrNull(),
                phone = me.phoneNumber?.takeIf { it.isNotBlank() }?.let { "+$it" }
            )
            if (_auth.value is AuthState.Ready) _auth.value = AuthState.Ready(account)
        }
        // Warm the chat list so private t.me/c links resolve on a fresh install.
        runCatching { send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) }
    }
}

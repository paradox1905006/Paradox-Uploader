package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.ApiCredentials
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.domain.telegram.ConnectionStatus
import com.dparadox.paradoxuploader.domain.telegram.TelegramAuthRepository
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.tgCall
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramAuthRepositoryImpl @Inject constructor(
    private val td: TdSession,
    private val vault: TelegramVault
) : TelegramAuthRepository {

    override val authState: StateFlow<AuthState> get() = td.authState
    override val connection: StateFlow<ConnectionStatus> get() = td.connection

    override fun start() = td.ensureStarted()

    override fun apiCredentials(): ApiCredentials? = vault.apiCredentials()

    override suspend fun saveApiCredentials(credentials: ApiCredentials): TgResult<Unit> {
        if (credentials.apiId <= 0 || credentials.apiHash.trim().length < 16) {
            return TgResult.Failure(TgError.ApiCredentialsMissing)
        }
        return tgCall {
            vault.saveApiCredentials(credentials)
            td.restart()
        }
    }

    override suspend fun submitPhone(phone: String) = tgCall {
        td.send(TdApi.SetAuthenticationPhoneNumber(phone, null))
        Unit
    }

    override suspend fun submitCode(code: String) = tgCall {
        td.send(TdApi.CheckAuthenticationCode(code))
        Unit
    }

    override suspend fun resendCode() = tgCall {
        td.send(newResendFunction())
        Unit
    }

    /**
     * Newer TDLib builds take a ResendCodeReason, older ones take no argument. Build whichever this
     * TDLib has, so the app compiles and works against both.
     */
    @Suppress("UNCHECKED_CAST")
    private fun newResendFunction(): TdApi.Function<TdApi.Ok> {
        val cls = TdApi.ResendAuthenticationCode::class.java
        val withReason = cls.constructors.firstOrNull { it.parameterTypes.size == 1 }
        if (withReason != null) {
            val reason = Class.forName("org.drinkless.tdlib.TdApi\$ResendCodeReasonUserRequest")
                .getDeclaredConstructor().newInstance()
            return withReason.newInstance(reason) as TdApi.Function<TdApi.Ok>
        }
        return cls.getDeclaredConstructor().newInstance() as TdApi.Function<TdApi.Ok>
    }

    override suspend fun submitPassword(password: String) = tgCall {
        td.send(TdApi.CheckAuthenticationPassword(password))
        Unit
    }

    override suspend fun logout() = tgCall {
        td.send(TdApi.LogOut())
        Unit
    }

    override suspend fun clearCache() = tgCall {
        val opt = TdApi.OptimizeStorage().apply {
            size = 0
            ttl = -1
            count = -1
            immunityDelay = 0
            chatLimit = 20
        }
        td.send(opt)
        Unit
    }
}

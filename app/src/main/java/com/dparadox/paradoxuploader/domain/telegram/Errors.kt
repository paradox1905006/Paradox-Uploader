package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class AuthFailure { PHONE_INVALID, CODE_INVALID, CODE_EXPIRED, PASSWORD_INVALID, OTHER }

sealed interface TgError {
    data object EmptyLink : TgError
    data object InvalidLink : TgError
    data object InviteLink : TgError
    data object MessageNotFound : TgError
    data object ChatNotAccessible : TgError
    data object PrivateChannelRestricted : TgError
    data object PermissionDenied : TgError
    data object ProtectedContent : TgError
    data object UnsupportedMedia : TgError
    data object NotLoggedIn : TgError
    data object ApiCredentialsMissing : TgError
    data object SessionExpired : TgError
    data object Network : TgError
    data object RateLimited : TgError
    data object LinkGenerationFailed : TgError
    data class AuthFailed(val reason: AuthFailure) : TgError
    data class FloodWait(val seconds: Int) : TgError
    data class Mtproto(val code: Int, val message: String) : TgError
    data class Unknown(val message: String?) : TgError
}

class TelegramException(val error: TgError, cause: Throwable? = null) : Exception(error.toString(), cause)

sealed interface TgResult<out T> {
    data class Success<T>(val value: T) : TgResult<T>
    data class Failure(val error: TgError) : TgResult<Nothing>
}

inline fun <T> tgCall(block: () -> T): TgResult<T> = try {
    TgResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: TelegramException) {
    TgResult.Failure(e.error)
} catch (e: IOException) {
    TgResult.Failure(TgError.Network)
} catch (e: Exception) {
    TgResult.Failure(TgError.Unknown(e.message))
}

fun Throwable.toTgError(): TgError = when (this) {
    is TelegramException -> error
    is IOException -> TgError.Network
    else -> TgError.Unknown(message)
}

package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.AuthFailure
import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TgError
import org.drinkless.tdlib.TdApi

object TdErrors {
    private val NUMBER = Regex("(\\d+)")

    fun map(e: TdApi.Error): TelegramException = TelegramException(toTgError(e.code, e.message.orEmpty()))

    fun toTgError(code: Int, rawMessage: String): TgError {
        val m = rawMessage.uppercase()
        val seconds = NUMBER.find(m)?.value?.toIntOrNull()
        return when {
            m.contains("FLOOD_WAIT") || m.contains("RETRY AFTER") ->
                if (seconds != null) TgError.FloodWait(seconds) else TgError.RateLimited
            code == 429 || code == 420 -> if (seconds != null) TgError.FloodWait(seconds) else TgError.RateLimited
            m.contains("PHONE_NUMBER_INVALID") -> TgError.AuthFailed(AuthFailure.PHONE_INVALID)
            m.contains("PHONE_CODE_INVALID") || m.contains("PHONE_CODE_EMPTY") -> TgError.AuthFailed(AuthFailure.CODE_INVALID)
            m.contains("PHONE_CODE_EXPIRED") -> TgError.AuthFailed(AuthFailure.CODE_EXPIRED)
            m.contains("PASSWORD_HASH_INVALID") || m.contains("PASSWORD_INVALID") -> TgError.AuthFailed(AuthFailure.PASSWORD_INVALID)
            m.contains("PHONE_NUMBER_BANNED") || m.contains("PHONE_NUMBER_FLOOD") -> TgError.AuthFailed(AuthFailure.OTHER)
            m.contains("AUTH_KEY") || m.contains("SESSION_REVOKED") || m.contains("SESSION_EXPIRED") ||
                m.contains("USER_DEACTIVATED") -> TgError.SessionExpired
            code == 401 -> TgError.SessionExpired
            m.contains("CHANNEL_PRIVATE") || m.contains("CHAT_FORBIDDEN") || m.contains("CHANNEL_BANNED") ||
                m.contains("USER_BANNED_IN_CHANNEL") || m.contains("CHAT_WRITE_FORBIDDEN") -> TgError.PrivateChannelRestricted
            m.contains("CHAT NOT FOUND") || m.contains("CHAT_ID_INVALID") || m.contains("PEER_ID_INVALID") ||
                m.contains("USERNAME_NOT_OCCUPIED") || m.contains("USERNAME_INVALID") ||
                m.contains("CHANNEL_INVALID") -> TgError.ChatNotAccessible
            m.contains("MESSAGE NOT FOUND") || m.contains("MESSAGE_ID_INVALID") || m.contains("MESSAGE_NOT_FOUND") -> TgError.MessageNotFound
            code == 403 -> TgError.PermissionDenied
            m.contains("NETWORK") || m.contains("TIMEOUT") || m.contains("CONNECTION") -> TgError.Network
            else -> TgError.Mtproto(code, rawMessage)
        }
    }
}

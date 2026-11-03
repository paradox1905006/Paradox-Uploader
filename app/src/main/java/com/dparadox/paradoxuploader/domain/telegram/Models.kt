package com.dparadox.paradoxuploader.domain.telegram

/** A parsed Telegram message link. [canonicalUrl] is always the https://t.me form TDLib understands. */
sealed interface TgLink {
    val messageId: Long
    val canonicalUrl: String

    data class Public(
        val username: String,
        override val messageId: Long,
        override val canonicalUrl: String
    ) : TgLink

    /** t.me/c/<channelId>/<messageId> (private channels and supergroups). */
    data class Private(
        val channelId: Long,
        override val messageId: Long,
        override val canonicalUrl: String
    ) : TgLink
}

data class Account(val displayName: String, val username: String?, val phone: String?)

data class ApiCredentials(val apiId: Int, val apiHash: String)

sealed interface AuthState {
    data object Initializing : AuthState
    data object NeedsApiCredentials : AuthState
    data object WaitPhone : AuthState
    data class WaitCode(val phone: String) : AuthState
    data class WaitPassword(val hint: String) : AuthState
    data class Ready(val account: Account?) : AuthState
    data object LoggingOut : AuthState
    data class Unsupported(val step: String) : AuthState
}

enum class ConnectionStatus { CONNECTED, CONNECTING, WAITING_FOR_NETWORK }

enum class FileKind { DOCUMENT, VIDEO, AUDIO, IMAGE, APK, ARCHIVE, VOICE, ANIMATION, OTHER }

data class ResolvedFile(
    val tdFileId: Int,
    val chatId: Long,
    val chatTitle: String,
    val messageId: Long,
    val dateMillis: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: FileKind,
    val thumbnailPath: String?,
    val sourceUrl: String
)

/** A loopback URL served by this app, backed by the user's own Telegram session. */
data class DirectLink(
    val url: String,
    val token: String,
    val fileName: String,
    val createdAt: Long,
    val expiresAt: Long
)

sealed interface DownloadProgress {
    data class Running(val bytes: Long, val total: Long) : DownloadProgress
    data class Done(val uri: String, val fileName: String) : DownloadProgress
}

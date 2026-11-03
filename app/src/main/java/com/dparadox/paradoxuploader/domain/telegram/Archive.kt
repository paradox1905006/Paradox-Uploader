package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.Flow

/** A channel the user manages as a personal cloud archive. Only the reference is stored locally. */
data class ArchiveChannel(
    val chatId: Long,
    val title: String,
    val isDefault: Boolean,
    val isPublic: Boolean,
    val createdAt: Long
)

/** One media message found while walking a channel's full history. */
data class ArchiveFile(
    val tdFileId: Int,
    val chatId: Long,
    val chatTitle: String,
    /** TDLib message id (server id shl 20). Used as the selection key. */
    val tdMessageId: Long,
    /** Telegram's public message id, as used in t.me links. */
    val serverMessageId: Long,
    val dateMillis: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: FileKind,
    /** False when the channel or sender restricts saving. Such files are listed but cannot be downloaded. */
    val canSave: Boolean
) {
    val key: Long get() = tdMessageId

    fun toResolved(): ResolvedFile = ResolvedFile(
        tdFileId = tdFileId,
        chatId = chatId,
        chatTitle = chatTitle,
        messageId = serverMessageId,
        dateMillis = dateMillis,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = kind,
        thumbnailPath = null,
        sourceUrl = "https://t.me/c/${privateChannelId(chatId)}/$serverMessageId"
    )
}

/** Converts a TDLib supergroup/channel chat id to the id used in t.me/c/ links. */
fun privateChannelId(chatId: Long): Long = -chatId - 1_000_000_000_000L

sealed interface ArchiveLoad {
    /** Emitted after each history page. [files] is the list so far, so the UI can fill in progressively. */
    data class Loading(val scanned: Int, val files: List<ArchiveFile>) : ArchiveLoad
    data class Done(val files: List<ArchiveFile>, val scanned: Int) : ArchiveLoad
    data class Failed(val error: TgError) : ArchiveLoad
}

interface ArchiveRepository {
    val channels: Flow<List<ArchiveChannel>>
    suspend fun createChannel(title: String, description: String, isPublic: Boolean, username: String?): TgResult<ArchiveChannel>
    suspend fun setDefault(chatId: Long): TgResult<Unit>
    /** Forgets the local reference only. The Telegram channel itself is not deleted. */
    suspend fun removeReference(chatId: Long): TgResult<Unit>
    /** Walks the whole channel history, newest first, emitting progress after every page. */
    fun loadFiles(chatId: Long): Flow<ArchiveLoad>
}

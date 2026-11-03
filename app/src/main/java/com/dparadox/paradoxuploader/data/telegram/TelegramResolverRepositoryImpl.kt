package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.FileKinds
import com.dparadox.paradoxuploader.domain.telegram.ResolvedFile
import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TelegramResolverRepository
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgLink
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.tgCall
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramResolverRepositoryImpl @Inject constructor(
    private val td: TdSession
) : TelegramResolverRepository {

    override suspend fun resolve(link: TgLink): TgResult<ResolvedFile> = tgCall {
        val message = fetchMessage(link)
        val chat = try {
            td.send(TdApi.GetChat(message.chatId))
        } catch (e: TelegramException) {
            throw if (e.error is TgError.Mtproto) TelegramException(TgError.ChatNotAccessible) else e
        }
        // Respect "Restrict saving content" set by the channel/group owner or the sender.
        if (!message.canBeSaved || chat.hasProtectedContent) throw TelegramException(TgError.ProtectedContent)

        val media = TdMediaExtractor.extract(message.content) ?: throw TelegramException(TgError.UnsupportedMedia)
        val size = media.file.size.takeIf { it > 0 } ?: media.file.expectedSize
        if (size <= 0) throw TelegramException(TgError.UnsupportedMedia)

        val serverId = message.id shr 20
        val kind = FileKinds.classify(media.name, media.mime, media.kind)
        val mime = media.mime.ifBlank { "application/octet-stream" }
        val name = FileKinds.sanitizeName(
            media.name.ifBlank { "telegram_${serverId}.${FileKinds.extensionFor(mime, kind)}" }
        )
        ResolvedFile(
            tdFileId = media.file.id,
            chatId = message.chatId,
            chatTitle = chat.title.orEmpty(),
            messageId = serverId,
            dateMillis = message.date * 1000L,
            fileName = name,
            mimeType = mime,
            sizeBytes = size,
            kind = kind,
            thumbnailPath = media.thumbnail?.let { thumbnailPath(it) },
            sourceUrl = link.canonicalUrl
        )
    }

    private suspend fun fetchMessage(link: TgLink): TdApi.Message {
        val info = try {
            td.send(TdApi.GetMessageLinkInfo(link.canonicalUrl))
        } catch (e: TelegramException) {
            null.also { if (e.error is TgError.FloodWait || e.error is TgError.Network || e.error is TgError.SessionExpired) throw e }
        }
        info?.message?.let { return it }

        // Fallback: address the chat directly.
        val chatId = when (link) {
            is TgLink.Private -> -1_000_000_000_000L - link.channelId
            is TgLink.Public -> td.send(TdApi.SearchPublicChat(link.username)).id
        }
        if (link is TgLink.Private) {
            // Throws ChatNotAccessible if the user is not a member / chat unknown.
            td.send(TdApi.GetChat(chatId))
        }
        return try {
            td.send(TdApi.GetMessage(chatId, link.messageId shl 20))
        } catch (e: TelegramException) {
            if (e.error is TgError.Mtproto) throw TelegramException(TgError.MessageNotFound) else throw e
        }
    }

    private suspend fun thumbnailPath(file: TdApi.File): String? {
        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) return file.local.path
        return withTimeoutOrNull(8_000) {
            runCatching { td.send(TdApi.DownloadFile(file.id, 1, 0, 0, true)).local.path }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
    }
}

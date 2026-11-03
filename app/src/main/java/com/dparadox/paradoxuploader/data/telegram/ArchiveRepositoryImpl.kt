package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.ArchiveChannel
import com.dparadox.paradoxuploader.domain.telegram.ArchiveFile
import com.dparadox.paradoxuploader.domain.telegram.ArchiveLoad
import com.dparadox.paradoxuploader.domain.telegram.ArchiveRepository
import com.dparadox.paradoxuploader.domain.telegram.FileKinds
import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.tgCall
import com.dparadox.paradoxuploader.domain.telegram.toTgError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveRepositoryImpl @Inject constructor(
    private val td: TdSession,
    private val store: ArchiveStore
) : ArchiveRepository {

    override val channels: Flow<List<ArchiveChannel>> = store.channels

    override suspend fun createChannel(
        title: String,
        description: String,
        isPublic: Boolean,
        username: String?
    ): TgResult<ArchiveChannel> = tgCall {
        // Telegram channels are private by default. isChannel = true makes a broadcast channel.
        val chat = td.send(
            TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false)
        )
        var publicNow = false
        if (isPublic && !username.isNullOrBlank()) {
            // A failed username claim should not lose the channel we just created; it stays private.
            val supergroupId = (chat.type as? TdApi.ChatTypeSupergroup)?.supergroupId
            if (supergroupId != null) {
                publicNow = runCatching {
                    td.send(TdApi.SetSupergroupUsername(supergroupId, username.trim().trimStart('@')))
                }.isSuccess
            }
        }
        val entry = ArchiveChannel(
            chatId = chat.id,
            title = chat.title.orEmpty(),
            isDefault = true,
            isPublic = publicNow,
            createdAt = System.currentTimeMillis()
        )
        // A newly created channel becomes the default archive channel.
        store.update { list -> list.map { it.copy(isDefault = false) } + entry }
        entry
    }

    override suspend fun setDefault(chatId: Long): TgResult<Unit> = tgCall {
        store.update { list -> list.map { it.copy(isDefault = it.chatId == chatId) } }
    }

    override suspend fun removeReference(chatId: Long): TgResult<Unit> = tgCall {
        store.update { list -> list.filterNot { it.chatId == chatId } }
    }

    override fun loadFiles(chatId: Long): Flow<ArchiveLoad> = flow {
        try {
            val chat = td.send(TdApi.GetChat(chatId))
            val found = ArrayList<ArchiveFile>()
            var scanned = 0
            var from = 0L // 0 = start from the newest message
            while (true) {
                val page = td.send(TdApi.GetChatHistory(chatId, from, 0, PAGE_SIZE, false))
                val msgs = page.messages?.filterNotNull().orEmpty()
                if (msgs.isEmpty()) break
                scanned += msgs.size
                for (m in msgs) toArchiveFile(m, chat.title.orEmpty(), chat.hasProtectedContent)?.let(found::add)
                val next = msgs.last().id
                if (next == from) break // guard against a stuck cursor
                from = next
                emit(ArchiveLoad.Loading(scanned, found.toList()))
            }
            emit(ArchiveLoad.Done(found.sortedByDescending { it.dateMillis }, scanned))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramException) {
            emit(ArchiveLoad.Failed(e.error))
        } catch (e: Throwable) {
            emit(ArchiveLoad.Failed(e.toTgError()))
        }
    }

    private fun toArchiveFile(m: TdApi.Message, chatTitle: String, protectedChat: Boolean): ArchiveFile? {
        val media = TdMediaExtractor.extract(m.content) ?: return null
        val size = media.file.size.takeIf { it > 0 } ?: media.file.expectedSize
        if (size <= 0) return null
        val mime = media.mime.ifBlank { "application/octet-stream" }
        val kind = FileKinds.classify(media.name, mime, media.kind)
        val serverId = m.id shr 20
        val name = FileKinds.sanitizeName(
            media.name.ifBlank { "telegram_$serverId.${FileKinds.extensionFor(mime, kind)}" }
        )
        return ArchiveFile(
            tdFileId = media.file.id,
            chatId = m.chatId,
            chatTitle = chatTitle,
            tdMessageId = m.id,
            serverMessageId = serverId,
            dateMillis = m.date * 1000L,
            fileName = name,
            mimeType = mime,
            sizeBytes = size,
            kind = kind,
            canSave = m.canBeSaved && !protectedChat
        )
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

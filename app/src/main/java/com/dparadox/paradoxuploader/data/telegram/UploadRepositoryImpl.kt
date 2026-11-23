package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.DestinationKind
import com.dparadox.paradoxuploader.domain.telegram.ForumTopic
import com.dparadox.paradoxuploader.domain.telegram.SentMessage
import com.dparadox.paradoxuploader.domain.telegram.TelegramException
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.UploadRepository
import com.dparadox.paradoxuploader.domain.telegram.tgCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val td: TdSession
) : UploadRepository {

    override suspend fun destinations(): TgResult<List<ChatDestination>> = tgCall {
        val ids = td.send(TdApi.GetChats(TdApi.ChatListMain(), 500)).chatIds.toList()
        val chats = coroutineScope {
            ids.map { id -> async { runCatching { td.send(TdApi.GetChat(id)) }.getOrNull() } }
                .awaitAll()
        }.filterNotNull()
        val out = mutableListOf<ChatDestination>()
        for (c in chats) toDestination(c)?.let { out.add(it) }
        out.sortedBy { it.title.lowercase() }
    }

    override suspend fun createChannel(
        title: String,
        description: String,
        isPublic: Boolean,
        username: String?
    ): TgResult<ChatDestination> = tgCall {
        val chat = td.send(TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false))
        if (isPublic && !username.isNullOrBlank()) {
            // A failed username claim keeps the channel, which then stays private.
            (chat.type as? TdApi.ChatTypeSupergroup)?.supergroupId?.let { sg ->
                runCatching { td.send(TdApi.SetSupergroupUsername(sg, username.trim().trimStart('@'))) }
            }
        }
        toDestination(chat) ?: throw TelegramException(TgError.ChatNotAccessible)
    }

    override suspend fun createGroup(
        title: String,
        description: String,
        forum: Boolean
    ): TgResult<ChatDestination> = tgCall {
        val chatId: Long = if (forum) {
            td.send(TdApi.CreateNewSupergroupChat(title, true, false, description, null, 0, false)).id
        } else {
            val created = td.send(TdApi.CreateNewBasicGroupChat(LongArray(0), title, 0))
            if (description.isNotBlank()) td.send(TdApi.SetChatDescription(created.chatId, description))
            created.chatId
        }
        toDestination(td.send(TdApi.GetChat(chatId))) ?: throw TelegramException(TgError.ChatNotAccessible)
    }

    // Topic support is temporarily stubbed: the TDLib forum-topic signatures in this build
    // could not be verified. Topics come back empty and creating one reports a clear message.
    override suspend fun searchChats(query: String): TgResult<List<ChatDestination>> = tgCall {
        val ids = td.send(TdApi.SearchChats(query, 50)).chatIds.toList()
        val out = mutableListOf<ChatDestination>()
        for (id in ids) {
            val chat = runCatching { td.send(TdApi.GetChat(id)) }.getOrNull() ?: continue
            toDestination(chat)?.let { out.add(it) }
        }
        out
    }

    override suspend fun topics(chatId: Long): TgResult<List<ForumTopic>> =
        TgResult.Success(emptyList())

    override suspend fun createTopic(chatId: Long, name: String, color: Int): TgResult<ForumTopic> =
        TgResult.Failure(TgError.Unknown("Creating topics is not available in this build yet"))

    override suspend fun sendFile(
        chatId: Long,
        topicId: Long?,
        path: String,
        caption: String,
        onFileId: (Int) -> Unit,
        onProgress: (uploaded: Long, total: Long) -> Unit
    ): TgResult<SentMessage> = tgCall {
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(path),
            null,
            false,
            TdApi.FormattedText(caption, emptyArray())
        )
        val topic: TdApi.MessageTopic? = topicId?.let { TdApi.MessageTopicForum(it.toInt()) }
        val temp = td.send(TdApi.SendMessage(chatId, topic, null, null, null, content))
        val fileId = (temp.content as? TdApi.MessageDocument)?.document?.document?.id
            ?: throw TelegramException(TgError.Unknown("Telegram did not return an upload id"))
        onFileId(fileId)

        val outcome = try { coroutineScope {
            // Progress and send results arrive as updates. Progress runs only while we wait for the outcome.
            val progress = launch(start = CoroutineStart.UNDISPATCHED) {
                td.fileUpdates.filter { it.file.id == fileId }.collect { u ->
                    val total = if (u.file.size > 0) u.file.size else u.file.expectedSize
                    onProgress(u.file.remote.uploadedSize, total)
                }
            }
            val result = td.sendUpdates.first { ev ->
                (ev is TdApi.UpdateMessageSendSucceeded && ev.oldMessageId == temp.id) ||
                    (ev is TdApi.UpdateMessageSendFailed && ev.oldMessageId == temp.id)
            }
            progress.cancel()
            result
        } } catch (e: CancellationException) {
            // Deleting a message that is still being sent is how TDLib cancels the upload. Without
            // this, a "cancelled" file kept uploading and was still posted to the chat.
            withContext(NonCancellable) {
                runCatching { td.send(TdApi.DeleteMessages(chatId, longArrayOf(temp.id), true)) }
            }
            throw e
        }

        when (outcome) {
            is TdApi.UpdateMessageSendSucceeded -> {
                val link = runCatching {
                    td.send(TdApi.GetMessageLink(chatId, outcome.message.id, 0, false, false)).link
                }.getOrNull()
                SentMessage(outcome.message.id, link)
            }
            is TdApi.UpdateMessageSendFailed ->
                throw outcome.error?.let { TdErrors.map(it) }
                    ?: TelegramException(TgError.Unknown("Telegram rejected the upload"))
            else -> throw TelegramException(TgError.Unknown("Unexpected send result"))
        }
    }

    override suspend fun cancelUpload(fileId: Int) {
        // This TDLib build has no CancelUploadFile. Cancelling the job stops our side; TDLib finishes its own queue.
        @Suppress("UNUSED_VARIABLE") val unused = fileId
    }

    private suspend fun toDestination(c: TdApi.Chat): ChatDestination? = when (val t = c.type) {
        is TdApi.ChatTypeSupergroup -> {
            val kind = when {
                t.isChannel -> DestinationKind.CHANNEL
                else -> {
                    val isForum = runCatching { td.send(TdApi.GetSupergroup(t.supergroupId)).isForum }.getOrDefault(false)
                    if (isForum) DestinationKind.FORUM else DestinationKind.SUPERGROUP
                }
            }
            ChatDestination(c.id, c.title.orEmpty(), kind)
        }
        is TdApi.ChatTypeBasicGroup -> ChatDestination(c.id, c.title.orEmpty(), DestinationKind.BASIC_GROUP)
        else -> null
    }
}

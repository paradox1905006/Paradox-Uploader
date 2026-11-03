package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.StateFlow

enum class DestinationKind { CHANNEL, SUPERGROUP, FORUM, BASIC_GROUP }

/** A channel or group the user can post to. */
data class ChatDestination(val chatId: Long, val title: String, val kind: DestinationKind) {
    val supportsTopics: Boolean get() = kind == DestinationKind.FORUM
    val typeLabel: String get() = when (kind) {
        DestinationKind.CHANNEL -> "Channel"
        DestinationKind.SUPERGROUP -> "Group"
        DestinationKind.FORUM -> "Forum group"
        DestinationKind.BASIC_GROUP -> "Basic group"
    }
}

data class ForumTopic(val threadId: Long, val name: String)

/** A file chosen by the user, before it is staged for upload. */
data class PickedFile(val uri: String, val name: String, val mime: String, val size: Long)

enum class UploadStatus { WAITING, UPLOADING, COMPLETED, FAILED, CANCELLED }

data class UploadJob(
    val id: String,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val caption: String,
    val chatId: Long,
    val chatTitle: String,
    val topicId: Long?,
    val topicName: String?,
    val status: UploadStatus,
    val uploadedBytes: Long = 0,
    val speedBps: Double = 0.0,
    val etaSeconds: Long? = null,
    val tdFileId: Int? = null,
    val messageId: Long? = null,
    val link: String? = null,
    val error: TgError? = null,
    val createdAt: Long
)

data class UploadSelection(val destination: ChatDestination?, val topic: ForumTopic?)

data class SentMessage(val messageId: Long, val link: String?)

interface UploadRepository {
    suspend fun destinations(): TgResult<List<ChatDestination>>
    suspend fun createChannel(title: String, description: String, isPublic: Boolean, username: String?): TgResult<ChatDestination>
    suspend fun createGroup(title: String, description: String, forum: Boolean): TgResult<ChatDestination>
    suspend fun topics(chatId: Long): TgResult<List<ForumTopic>>
    /** Channels and groups whose name matches [query]. */
    suspend fun searchChats(query: String): TgResult<List<ChatDestination>>
    suspend fun createTopic(chatId: Long, name: String, color: Int): TgResult<ForumTopic>

    /**
     * Sends [path] as a document. [onFileId] fires once TDLib has assigned the upload file id,
     * [onProgress] reports uploaded and total bytes. Suspends until Telegram confirms or rejects the message.
     */
    suspend fun sendFile(
        chatId: Long,
        topicId: Long?,
        path: String,
        caption: String,
        onFileId: (Int) -> Unit,
        onProgress: (uploaded: Long, total: Long) -> Unit
    ): TgResult<SentMessage>

    suspend fun cancelUpload(fileId: Int)
}

interface UploadController {
    val selection: StateFlow<UploadSelection>
    val recent: StateFlow<List<ChatDestination>>
    val jobs: StateFlow<List<UploadJob>>
    val paused: StateFlow<Boolean>

    fun selectDestination(destination: ChatDestination)
    fun selectTopic(topic: ForumTopic?)
    /** Queues [files] for the current destination and topic. Does nothing if no destination is selected. */
    fun enqueue(files: List<PickedFile>, caption: String)
    /** Pauses or resumes the queue. Pausing stops new files from starting; an in-flight upload finishes. */
    fun setPaused(paused: Boolean)
    fun retry(id: String)
    fun cancel(id: String)
    fun clearFinished()
}

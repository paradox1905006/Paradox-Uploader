package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveArchiveChannels @Inject constructor(private val repo: ArchiveRepository) {
    operator fun invoke(): Flow<List<ArchiveChannel>> = repo.channels
}

class CreateArchiveChannel @Inject constructor(private val repo: ArchiveRepository) {
    suspend operator fun invoke(
        title: String,
        description: String,
        isPublic: Boolean,
        username: String?
    ): TgResult<ArchiveChannel> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return TgResult.Failure(TgError.InvalidLink)
        return repo.createChannel(cleanTitle.take(128), description.trim().take(255), isPublic, username)
    }
}

class LoadArchiveFiles @Inject constructor(private val repo: ArchiveRepository) {
    operator fun invoke(chatId: Long): Flow<ArchiveLoad> = repo.loadFiles(chatId)
}

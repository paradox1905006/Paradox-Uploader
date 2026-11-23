package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TelegramAuthRepository {
    val authState: StateFlow<AuthState>
    val connection: StateFlow<ConnectionStatus>
    fun start()
    fun apiCredentials(): ApiCredentials?
    suspend fun saveApiCredentials(credentials: ApiCredentials): TgResult<Unit>
    suspend fun submitPhone(phone: String): TgResult<Unit>
    suspend fun submitCode(code: String): TgResult<Unit>
    suspend fun resendCode(): TgResult<Unit>
    suspend fun submitPassword(password: String): TgResult<Unit>
    suspend fun logout(): TgResult<Unit>
    suspend fun clearCache(): TgResult<Unit>
}

interface TelegramResolverRepository {
    suspend fun resolve(link: TgLink): TgResult<ResolvedFile>
}

interface DirectLinkRepository {
    /** Number of links currently valid. */
    val activeLinks: StateFlow<Int>
    suspend fun create(file: ResolvedFile, ttlMinutes: Int): TgResult<DirectLink>
    fun revoke(token: String)
    fun revokeAll()
}

interface FileDownloadRepository {
    /** Streams the file from Telegram into the device's Downloads. Throws [TelegramException] on failure. */
    fun download(file: ResolvedFile): Flow<DownloadProgress>
}

interface TelegramPrefs {
    val linkTtlMinutes: Flow<Int>
    suspend fun setLinkTtlMinutes(minutes: Int)
}

/** Random-access reads of a Telegram file through the authenticated session. */
interface RemoteFileReader {
    /** Returns up to [maxBytes] bytes starting at [position]; an empty array means end of file. */
    suspend fun read(fileId: Int, position: Long, maxBytes: Int): ByteArray
}

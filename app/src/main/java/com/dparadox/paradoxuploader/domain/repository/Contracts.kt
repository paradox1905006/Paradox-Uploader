package com.dparadox.paradoxuploader.domain.repository

import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.CleanupScan
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.model.EnqueueResult
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.model.UpdateInfo
import com.dparadox.paradoxuploader.domain.model.HistoryResult
import com.dparadox.paradoxuploader.domain.model.KeyTestResult
import com.dparadox.paradoxuploader.domain.model.PollOutcome
import com.dparadox.paradoxuploader.domain.model.SourceInfo
import com.dparadox.paradoxuploader.domain.model.TreeListing
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadMeta
import com.dparadox.paradoxuploader.domain.model.UploadOutcome
import com.dparadox.paradoxuploader.domain.model.UploadProgress
import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody
import java.io.InputStream

/** Secure storage for the archive.org S3 keys. */
interface KeyStore {
    fun get(): Credentials?
    fun save(credentials: Credentials)
    fun clear()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(block: (AppSettings) -> AppSettings)
}

/** Access to the files being uploaded (SAF uris or app-private staged copies). */
interface SourceFiles {
    fun describe(uri: String): SourceInfo?
    fun open(uri: String): InputStream

    /** Makes sure the uri stays readable later; returns the uri to store (may be a staged copy). */
    fun retainAccess(uri: String): String
    fun release(uri: String)

    /** Lists every file inside a device folder (a SAF tree uri). Null when it cannot be read. */
    fun listTree(uri: String): TreeListing? = null

    /** Keeps a picked device folder readable so its files can be uploaded later without copying. */
    fun retainTree(uri: String) {}
}

interface WorkScheduler {
    fun enqueueUpload(id: Long, wifiOnly: Boolean, replace: Boolean, chargingOnly: Boolean = false)
    fun cancelUpload(id: Long)

    /** Stops every running or waiting upload job (Pause all). The queue in Room is untouched. */
    fun cancelAllUploads() {}
    fun enqueuePoll(id: Long, delayMs: Long, slot: Int, replace: Boolean)
    fun cancelPoll(id: Long)
}

data class UploadRequest(
    val identifier: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val title: String?,
    val description: String?,
    val tags: String?,
    val collection: String,
    val mediatype: String,
    val licenseUrl: String? = null
)

data class RemoteFile(
    val name: String,
    val sizeBytes: Long?,
    val format: String?,
    val mtime: Long?,
    val source: String? = null
)

interface ArchiveService {
    suspend fun upload(credentials: Credentials, request: UploadRequest, body: RequestBody): ApiResult<Unit>
    suspend fun deleteFile(credentials: Credentials, identifier: String, fileName: String): ApiResult<Unit>
    suspend fun fetchMetadata(identifier: String): ApiResult<List<RemoteFile>>
    suspend fun isDownloadable(identifier: String, fileName: String): ApiResult<Boolean>

    /** Changes the metadata of an item that already exists on archive.org. Blank fields are left alone. */
    suspend fun updateItemMetadata(credentials: Credentials, identifier: String, meta: FolderMeta): ApiResult<Unit> =
        ApiResult.Failure(ErrorKind.UNKNOWN, null, "Not supported")
}

/** Looks up the latest published app version. */
interface UpdateService {
    suspend fun latest(): ApiResult<UpdateInfo>
}

interface UploadRepository {
    fun observeItems(): Flow<List<UploadItem>>
    suspend fun getItem(id: Long): UploadItem?
    suspend fun enqueue(uris: List<String>, meta: UploadMeta): EnqueueResult
    suspend fun runUpload(id: Long, onProgress: (UploadProgress) -> Unit): UploadOutcome
    suspend fun markFailed(id: Long, code: String)
    suspend fun runPoll(id: Long): PollOutcome
    suspend fun retry(id: Long)
    suspend fun cancel(id: Long)
    suspend fun delete(id: Long): ApiResult<Unit>
    suspend fun checkNow(id: Long)
    suspend fun refresh(): ApiResult<Unit>
    suspend fun testKeys(credentials: Credentials): KeyTestResult
    suspend fun resumePending()

    /** Pause all: marks uploads as paused and stops the running jobs. Files stay queued. */
    suspend fun pauseAll()

    /** Resume all: queued files start again (an interrupted file restarts from the beginning). */
    suspend fun resumeAll()

    /** Re-schedules queued uploads so a changed Wi-Fi / charging setting applies to them too. */
    suspend fun applyUploadPolicy()

    /** Pushes a folder's default metadata to the item that already exists on archive.org. */
    suspend fun pushFolderMetadata(identifier: String, meta: FolderMeta): ApiResult<Unit>
}

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    suspend fun get(id: Long): Folder?
    suspend fun findByName(name: String): Folder?
    suspend fun create(name: String, meta: FolderMeta = FolderMeta()): Folder?

    /** Renames a folder and/or changes its default metadata. Returns the updated folder. */
    suspend fun update(id: Long, name: String, meta: FolderMeta): Folder?

    /** Removes the folder from the app only. Files already on archive.org stay where they are. */
    suspend fun delete(id: Long)

    /** Removes the folder when no upload belongs to it any more. Returns true when it was removed. */
    suspend fun deleteIfEmpty(id: Long): Boolean

    /** Removes every folder without files that is at least [minAgeMs] old. Returns how many were removed. */
    suspend fun removeEmpty(minAgeMs: Long): Int
}

interface CleanupRepository {
    /** Call after a file was deleted. True when no history entry uses [identifier] any more. */
    suspend fun onFileDeleted(identifier: String): Boolean
    suspend fun markPossiblyEmpty(identifier: String)

    /** Checks on archive.org which of the remembered items really have no files left. */
    suspend fun scanEmptyItems(): CleanupScan
    suspend fun dismiss(identifier: String)
    suspend fun dismissAll()
}

interface HistoryRepository {
    suspend fun exportToDocument(uri: String): HistoryResult
    suspend fun importFromDocument(uri: String): HistoryResult

    /** Two-way merge with a user-chosen file (for example in Google Drive), then writes it back. */
    suspend fun syncWithDocument(uri: String): HistoryResult

    /** Looks for files missing from the history in the items and folders the app already knows. */
    suspend fun syncWithArchive(): HistoryResult

    /** Adds every original file of an archive.org item (link or identifier) to the history. */
    suspend fun addFromArchive(input: String): HistoryResult
}

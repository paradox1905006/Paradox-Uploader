package com.dparadox.paradoxuploader.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.dparadox.paradoxuploader.data.local.FolderDao
import com.dparadox.paradoxuploader.data.local.FolderEntity
import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.data.local.UploadEntity
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.HistoryResult
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.HistoryRepository
import com.dparadox.paradoxuploader.domain.repository.RemoteFile
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.RemoteFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class HistoryFile(
    val app: String,
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val folders: List<HistoryFolder> = emptyList(),
    val items: List<HistoryItem> = emptyList()
)

@Serializable
internal data class HistoryFolder(
    val name: String,
    val identifier: String,
    val createdAt: Long = 0L,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val licenseUrl: String? = null
)

@Serializable
internal data class HistoryItem(
    val identifier: String,
    val fileName: String,
    val sizeBytes: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val createdAt: Long = 0L,
    val status: String = "READY",
    val directUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val collection: String = "opensource",
    val mediatype: String = "data",
    val folderIdentifier: String? = null
)

/**
 * Export / import / sync of the upload history. The file never contains access keys.
 * Merging only ever adds entries; nothing already in the app is changed or removed.
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploads: UploadDao,
    private val folders: FolderDao,
    private val scheduler: WorkScheduler,
    private val api: ArchiveService,
    private val settings: SettingsRepository,
    private val clock: Clock
) : HistoryRepository {

    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // region export / import

    override suspend fun exportToDocument(uri: String): HistoryResult = withContext(Dispatchers.IO) {
        val file = buildExport()
        if (writeText(uri, format.encodeToString(file))) {
            HistoryResult.Success(added = 0, total = file.items.size)
        } else {
            HistoryResult.WriteFailed
        }
    }

    override suspend fun importFromDocument(uri: String): HistoryResult = withContext(Dispatchers.IO) {
        val text = readText(uri) ?: return@withContext HistoryResult.ReadFailed
        val file = decode(text) ?: return@withContext HistoryResult.InvalidFile
        HistoryResult.Success(added = merge(file), total = file.items.size)
    }

    // endregion

    // region sync

    override suspend fun syncWithDocument(uri: String): HistoryResult = withContext(Dispatchers.IO) {
        val text = readText(uri) ?: return@withContext HistoryResult.ReadFailed
        var added = 0
        if (text.isNotBlank()) {
            // A file that is not ours is never overwritten.
            val file = decode(text) ?: return@withContext HistoryResult.InvalidFile
            added = merge(file)
        }
        val export = buildExport()
        if (!writeText(uri, format.encodeToString(export))) return@withContext HistoryResult.WriteFailed
        val now = clock.nowMs()
        settings.update { it.copy(lastSyncAt = now) }
        HistoryResult.Success(added = added, total = export.items.size)
    }

    override suspend fun syncWithArchive(): HistoryResult = withContext(Dispatchers.IO) {
        val tracked = setOf(UploadStatus.READY, UploadStatus.PROCESSING, UploadStatus.DELAYED)
        val identifiers = (
            uploads.getAll().filter { it.status in tracked }.map { it.identifier } +
                folders.getAll().map { it.identifier }
            ).distinct()
        var added = 0
        var failure: ErrorKind? = null
        for (identifier in identifiers) {
            when (val result = api.fetchMetadata(identifier)) {
                is ApiResult.Failure -> {
                    if (result.kind != ErrorKind.NOT_FOUND && failure == null) failure = result.kind
                }
                is ApiResult.Success -> added += addRemoteFiles(identifier, result.value)
            }
        }
        val error = failure
        if (added == 0 && error != null) HistoryResult.Remote(error) else HistoryResult.Success(added, identifiers.size)
    }

    override suspend fun addFromArchive(input: String): HistoryResult = withContext(Dispatchers.IO) {
        val identifier = parseIdentifier(input) ?: return@withContext HistoryResult.InvalidFile
        when (val result = api.fetchMetadata(identifier)) {
            is ApiResult.Failure -> HistoryResult.Remote(result.kind)
            is ApiResult.Success -> HistoryResult.Success(added = addRemoteFiles(identifier, result.value), total = result.value.size)
        }
    }

    // endregion

    private suspend fun buildExport(): HistoryFile {
        val folderList = folders.getAll()
        val identifierById = folderList.associate { it.id to it.identifier }
        val onArchive = setOf(UploadStatus.READY, UploadStatus.PROCESSING, UploadStatus.DELAYED)
        val items = uploads.getAll()
            .filter { it.status in onArchive }
            .map {
                HistoryItem(
                    identifier = it.identifier,
                    fileName = it.fileName,
                    sizeBytes = it.sizeBytes,
                    mimeType = it.mimeType,
                    createdAt = it.createdAt,
                    status = it.status.name,
                    directUrl = it.directUrl,
                    title = it.title,
                    description = it.description,
                    tags = it.tags,
                    collection = it.collection,
                    mediatype = it.mediatype,
                    folderIdentifier = it.folderId?.let { id -> identifierById[id] }
                )
            }
        return HistoryFile(
            app = APP_MARKER,
            version = 1,
            exportedAt = clock.nowMs(),
            folders = folderList.map {
                HistoryFolder(it.name, it.identifier, it.createdAt, it.title, it.description, it.tags, it.licenseUrl)
            },
            items = items
        )
    }

    private fun decode(text: String): HistoryFile? = try {
        format.decodeFromString<HistoryFile>(text).takeIf { it.app == APP_MARKER }
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Adds what is missing locally. Returns how many history entries were added. */
    private suspend fun merge(file: HistoryFile): Int {
        val existing = HashSet<String>()
        uploads.getAll().forEach { existing.add(key(it.identifier, it.fileName)) }
        val folderIds = HashMap<String, Long>()
        folders.getAll().forEach { folderIds[it.identifier] = it.id }
        val now = clock.nowMs()

        for (folder in file.folders) {
            if (!ARCHIVE_ID.matches(folder.identifier) || folder.identifier in folderIds) continue
            folderIds[folder.identifier] = folders.insert(
                FolderEntity(
                    name = folder.name.ifBlank { folder.identifier },
                    identifier = folder.identifier,
                    createdAt = if (folder.createdAt > 0) folder.createdAt else now,
                    title = folder.title,
                    description = folder.description,
                    tags = folder.tags,
                    licenseUrl = folder.licenseUrl
                )
            )
        }

        var added = 0
        for (item in file.items) {
            if (!ARCHIVE_ID.matches(item.identifier) || item.fileName.isBlank()) continue
            if (!existing.add(key(item.identifier, item.fileName))) continue
            val ready = item.status == UploadStatus.READY.name
            val id = uploads.insert(
                UploadEntity(
                    identifier = item.identifier,
                    fileName = item.fileName,
                    sizeBytes = item.sizeBytes.coerceAtLeast(0L),
                    mimeType = item.mimeType,
                    createdAt = if (item.createdAt > 0) item.createdAt else now,
                    status = if (ready) UploadStatus.READY else UploadStatus.PROCESSING,
                    directUrl = if (ready) {
                        item.directUrl?.takeIf { it.isNotBlank() } ?: ArchiveUrls.downloadUrl(item.identifier, item.fileName)
                    } else {
                        null
                    },
                    title = item.title,
                    description = item.description,
                    tags = item.tags,
                    collection = item.collection,
                    mediatype = item.mediatype,
                    processingStartedAt = now,
                    folderId = item.folderIdentifier?.let { folderIds[it] }
                )
            )
            // Not confirmed on archive.org yet: let the normal polling check it.
            if (!ready) scheduler.enqueuePoll(id, 0L, 0, replace = false)
            added++
        }
        return added
    }

    private suspend fun addRemoteFiles(identifier: String, files: List<RemoteFile>): Int {
        val local = uploads.getAll().filter { it.identifier == identifier }
        val existing = HashSet<String>()
        local.forEach { existing.add(it.fileName) }
        val template = local.firstOrNull()
        val folderId = folders.findByIdentifier(identifier)?.id ?: template?.folderId
        val now = clock.nowMs()
        var added = 0
        for (file in files) {
            if (!RemoteFiles.isOriginal(file) || file.name in existing) continue
            uploads.insert(
                UploadEntity(
                    identifier = identifier,
                    fileName = file.name,
                    sizeBytes = file.sizeBytes ?: 0L,
                    mimeType = guessMime(file.name),
                    createdAt = file.mtime?.let { it * 1000L } ?: now,
                    status = UploadStatus.READY,
                    directUrl = ArchiveUrls.downloadUrl(identifier, file.name),
                    lastCheckedAt = now,
                    processingStartedAt = now,
                    collection = template?.collection ?: "opensource",
                    mediatype = template?.mediatype ?: "data",
                    folderId = folderId
                )
            )
            existing.add(file.name)
            added++
        }
        return added
    }

    private fun parseIdentifier(input: String): String? {
        val trimmed = input.trim()
        val fromUrl = URL_ID.find(trimmed)?.groupValues?.get(1)
        return (fromUrl ?: trimmed).takeIf { ARCHIVE_ID.matches(it) }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun key(identifier: String, fileName: String) = "$identifier\u0000$fileName"

    private fun readText(uri: String): String? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun writeText(uri: String, text: String): Boolean = try {
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
        if (stream == null) {
            false
        } else {
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        }
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val APP_MARKER = "ParadoxUploader"
        val ARCHIVE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$")
        val URL_ID = Regex("archive\\.org/(?:details|download|metadata)/([A-Za-z0-9._-]+)")
    }
}

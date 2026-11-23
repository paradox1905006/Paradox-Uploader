package com.dparadox.paradoxuploader.data.repository

import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.data.local.UploadEntity
import com.dparadox.paradoxuploader.data.local.toDomain
import com.dparadox.paradoxuploader.data.local.toRequest
import com.dparadox.paradoxuploader.data.remote.ProgressRequestBody
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.model.EnqueueError
import com.dparadox.paradoxuploader.domain.model.EnqueueResult
import com.dparadox.paradoxuploader.domain.model.ErrorCodes
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.model.IdentifierMode
import com.dparadox.paradoxuploader.domain.model.KeyTestResult
import com.dparadox.paradoxuploader.domain.model.PollOutcome
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadMeta
import com.dparadox.paradoxuploader.domain.model.UploadOutcome
import com.dparadox.paradoxuploader.domain.model.UploadProgress
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.KeyStore
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.SourceFiles
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.repository.UploadRequest
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.IdentifierGenerator
import com.dparadox.paradoxuploader.domain.util.IdentifierValidator
import com.dparadox.paradoxuploader.domain.util.PollingSchedule
import com.dparadox.paradoxuploader.domain.util.ProgressTracker
import com.dparadox.paradoxuploader.domain.util.RetryPolicy
import com.dparadox.paradoxuploader.domain.util.SpeedMeter
import com.dparadox.paradoxuploader.domain.util.StatusMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val dao: UploadDao,
    private val api: ArchiveService,
    private val keys: KeyStore,
    private val settings: SettingsRepository,
    private val scheduler: WorkScheduler,
    private val sources: SourceFiles,
    private val tracker: ProgressTracker,
    private val clock: Clock
) : UploadRepository {

    override fun observeItems(): Flow<List<UploadItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getItem(id: Long): UploadItem? = dao.get(id)?.toDomain()

    override suspend fun enqueue(uris: List<String>, meta: UploadMeta): EnqueueResult =
        withContext(Dispatchers.IO) {
            if (keys.get() == null) return@withContext EnqueueResult(0, uris.size, EnqueueError.NO_KEYS)
            val current = settings.current()
            val folder = meta.folder
            if (folder == null && current.identifierMode == IdentifierMode.FIXED &&
                !IdentifierValidator.isValid(current.fixedIdentifier)
            ) {
                return@withContext EnqueueResult(0, uris.size, EnqueueError.INVALID_IDENTIFIER)
            }
            // Inside a folder every file shares one item, so names must be unique within it.
            val takenNames = mutableSetOf<String>()
            if (folder != null) {
                dao.getAll().filter { it.identifier == folder.identifier }.forEach { takenNames.add(it.fileName) }
            }
            var queued = 0
            var skipped = 0
            for (uri in uris) {
                val info = sources.describe(uri)
                if (info == null) {
                    skipped++
                    continue
                }
                val kept = try {
                    sources.retainAccess(uri)
                } catch (e: java.io.IOException) {
                    skipped++
                    continue
                } catch (e: SecurityException) {
                    skipped++
                    continue
                }
                val identifier = when {
                    folder != null -> folder.identifier
                    current.identifierMode == IdentifierMode.FIXED -> current.fixedIdentifier
                    else -> IdentifierGenerator.newItem()
                }
                var fileName = ArchiveUrls.sanitizeFileName(info.name)
                // Skip a real duplicate only: the same source already queued, or the same name + size
                // going into the same archive.org item (it would overwrite itself). Different files that
                // just share a name/size, or a file sent to a new item, must still be queued.
                val existing = dao.getAll()
                val alreadyTracked = existing.any {
                    it.status != UploadStatus.FAILED && (
                        (it.sourceUri != null && (it.sourceUri == uri || it.sourceUri == kept)) ||
                            (it.identifier == identifier && it.fileName == fileName && it.sizeBytes == info.sizeBytes)
                        )
                }
                if (alreadyTracked && folder == null) {
                    if (kept != uri) sources.release(kept)
                    skipped++
                    continue
                }
                if (folder != null) {
                    fileName = ArchiveUrls.uniqueName(fileName, takenNames)
                    takenNames.add(fileName)
                }
                val id = dao.insert(
                    UploadEntity(
                        identifier = identifier,
                        fileName = fileName,
                        sizeBytes = info.sizeBytes,
                        mimeType = info.mimeType,
                        createdAt = clock.nowMs(),
                        status = UploadStatus.UPLOADING,
                        sourceUri = kept,
                        // Values typed for this upload win; otherwise the folder's defaults apply.
                        title = meta.title?.takeIf { it.isNotBlank() }
                            ?: folder?.title?.takeIf { it.isNotBlank() }
                            ?: folder?.name,
                        description = meta.description?.takeIf { it.isNotBlank() }
                            ?: folder?.description?.takeIf { it.isNotBlank() },
                        tags = meta.tags?.takeIf { it.isNotBlank() }
                            ?: folder?.tags?.takeIf { it.isNotBlank() },
                        collection = current.defaultCollection.ifBlank { "opensource" },
                        mediatype = current.defaultMediatype.ifBlank { "data" },
                        folderId = folder?.id,
                        licenseUrl = folder?.licenseUrl?.takeIf { it.isNotBlank() }
                    )
                )
                if (!current.uploadsPaused) {
                    scheduler.enqueueUpload(id, current.wifiOnly, replace = true, chargingOnly = current.chargingOnly)
                }
                queued++
            }
            EnqueueResult(queued, skipped, null)
        }

    override suspend fun runUpload(id: Long, onProgress: (UploadProgress) -> Unit): UploadOutcome {
        val item = dao.get(id) ?: return UploadOutcome.Done
        if (item.status != UploadStatus.UPLOADING) return UploadOutcome.Done
        val credentials = keys.get() ?: return fail(id, ErrorCodes.NO_KEYS)
        val sourceUri = item.sourceUri
        if (sourceUri == null || sources.describe(sourceUri) == null) {
            return fail(id, ErrorCodes.SOURCE_MISSING)
        }
        // A previous attempt may have reached archive.org even though the response was lost.
        // If the exact file (same name and size) is already there, finish instead of uploading again.
        if (alreadyOnArchive(item, credentials)) {
            completeUpload(id, sourceUri)
            return UploadOutcome.Done
        }
        val request = item.toRequest()
        repeat(RetryPolicy.MAX_SLOWDOWN_RETRIES) { attempt ->
            val meter = SpeedMeter(clock)
            val body = ProgressRequestBody(
                type = item.mimeType.toMediaTypeOrNull(),
                length = item.sizeBytes.takeIf { it > 0 } ?: -1L,
                opener = { sources.open(sourceUri) }
            ) { sent ->
                meter.update(sent, item.sizeBytes)?.let {
                    tracker.update(id, it)
                    onProgress(it)
                }
            }
            when (val result = api.upload(credentials, request, body)) {
                is ApiResult.Success -> {
                    completeUpload(id, sourceUri)
                    return UploadOutcome.Done
                }
                is ApiResult.Failure -> when (result.kind) {
                    ErrorKind.SLOW_DOWN -> delay(RetryPolicy.delayMs(attempt))
                    ErrorKind.NETWORK, ErrorKind.SERVER -> return UploadOutcome.RetryLater
                    ErrorKind.AUTH -> return fail(id, ErrorCodes.AUTH)
                    ErrorKind.IDENTIFIER -> return fail(id, ErrorCodes.IDENTIFIER)
                    else -> return fail(id, result.message.ifBlank { ErrorCodes.UNKNOWN })
                }
            }
        }
        return UploadOutcome.RetryLater
    }

    private suspend fun alreadyOnArchive(item: UploadEntity, credentials: Credentials): Boolean {
        val remote = api.fetchMetadata(item.identifier)
        if (remote !is ApiResult.Success) return false
        return remote.value.any { it.name == item.fileName && it.sizeBytes == item.sizeBytes }
    }

    private suspend fun completeUpload(id: Long, sourceUri: String) {
        val now = clock.nowMs()
        mutate(id) {
            copy(
                status = StatusMachine.transition(status, UploadStatus.PROCESSING),
                processingStartedAt = now,
                errorMessage = null,
                lastCheckedAt = null,
                sourceUri = null
            )
        }
        tracker.clear(id)
        sources.release(sourceUri)
        scheduler.enqueuePoll(id, PollingSchedule.nextDelayMs(0) ?: 10_000L, 0, replace = true)
    }

    private suspend fun fail(id: Long, code: String): UploadOutcome {
        markFailed(id, code)
        return UploadOutcome.Failed(code)
    }

    override suspend fun markFailed(id: Long, code: String) {
        mutate(id) {
            if (StatusMachine.canTransition(status, UploadStatus.FAILED)) {
                copy(status = UploadStatus.FAILED, errorMessage = code)
            } else {
                this
            }
        }
        tracker.clear(id)
    }

    override suspend fun runPoll(id: Long): PollOutcome {
        val entity = dao.get(id) ?: return PollOutcome.Gone
        if (entity.status != UploadStatus.PROCESSING && entity.status != UploadStatus.DELAYED) {
            return PollOutcome.Gone
        }
        val now = clock.nowMs()
        val check = checkReady(entity)
        if (check is ApiResult.Success && check.value) {
            val updated = entity.copy(
                status = StatusMachine.transition(entity.status, UploadStatus.READY),
                directUrl = ArchiveUrls.downloadUrl(entity.identifier, entity.fileName),
                lastCheckedAt = now,
                errorMessage = null
            )
            dao.update(updated)
            return PollOutcome.Ready(updated.toDomain())
        }
        val started = entity.processingStartedAt ?: entity.createdAt
        val next = PollingSchedule.nextDelayMs(now - started)
        if (next == null) {
            // Still not visible after the normal schedule: show DELAYED but keep checking slowly.
            val status = if (entity.status == UploadStatus.DELAYED) {
                UploadStatus.DELAYED
            } else {
                StatusMachine.transition(entity.status, UploadStatus.DELAYED)
            }
            dao.update(entity.copy(status = status, lastCheckedAt = now))
            return PollOutcome.Pending(PollingSchedule.SLOW_RECHECK_MS)
        }
        dao.update(entity.copy(lastCheckedAt = now))
        return PollOutcome.Pending(next)
    }

    private suspend fun checkReady(entity: UploadEntity): ApiResult<Boolean> {
        val metadata = api.fetchMetadata(entity.identifier)
        if (metadata is ApiResult.Failure) return metadata
        val files = (metadata as ApiResult.Success).value
        val match = files.firstOrNull { it.name.equals(entity.fileName, ignoreCase = true) }
            ?: return ApiResult.Success(false)
        // Listed in the item with the same size: the bytes are on archive.org. The /download/ HEAD check
        // is not reliable for brand-new files, so it is no longer required.
        val sizeOk = match.sizeBytes == null || entity.sizeBytes <= 0 || match.sizeBytes == entity.sizeBytes
        return ApiResult.Success(sizeOk)
    }

    override suspend fun retry(id: Long) {
        val entity = dao.get(id) ?: return
        if (!StatusMachine.canTransition(entity.status, UploadStatus.UPLOADING)) return
        mutate(id) { copy(status = UploadStatus.UPLOADING, errorMessage = null) }
        val current = settings.current()
        if (!current.uploadsPaused) {
            scheduler.enqueueUpload(id, current.wifiOnly, replace = true, chargingOnly = current.chargingOnly)
        }
    }

    override suspend fun cancel(id: Long) {
        val entity = dao.get(id)
        scheduler.cancelUpload(id)
        scheduler.cancelPoll(id)
        entity?.sourceUri?.let { sources.release(it) }
        tracker.clear(id)
        dao.delete(listOf(id))
    }

    override suspend fun delete(id: Long): ApiResult<Unit> {
        val entity = dao.get(id) ?: return ApiResult.Success(Unit)
        if (entity.status == UploadStatus.UPLOADING) {
            cancel(id)
            return ApiResult.Success(Unit)
        }
        if (entity.processingStartedAt != null) {
            val credentials = keys.get() ?: return ApiResult.Failure(ErrorKind.AUTH, null, "")
            val result = api.deleteFile(credentials, entity.identifier, entity.fileName)
            if (result is ApiResult.Failure && result.kind != ErrorKind.NOT_FOUND) return result
        } else {
            entity.sourceUri?.let { sources.release(it) }
        }
        scheduler.cancelPoll(id)
        dao.delete(listOf(id))
        return ApiResult.Success(Unit)
    }

    override suspend fun checkNow(id: Long) {
        val entity = dao.get(id) ?: return
        if (entity.status != UploadStatus.PROCESSING && entity.status != UploadStatus.DELAYED) return
        scheduler.cancelPoll(id)
        scheduler.enqueuePoll(id, 0L, 0, replace = true)
    }

    override suspend fun refresh(): ApiResult<Unit> {
        val tracked = setOf(UploadStatus.READY, UploadStatus.PROCESSING, UploadStatus.DELAYED)
        val items = dao.getAll().filter { it.status in tracked }
        var failure: ApiResult.Failure? = null
        for ((identifier, group) in items.groupBy { it.identifier }) {
            when (val result = api.fetchMetadata(identifier)) {
                is ApiResult.Failure -> if (failure == null) failure = result
                is ApiResult.Success -> {
                    val byName = result.value.associateBy { it.name }
                    for (entity in group) {
                        val remote = byName[entity.fileName] ?: continue
                        val remoteSize = remote.sizeBytes
                        if (remoteSize != null && remoteSize != entity.sizeBytes) {
                            mutate(entity.id) { copy(sizeBytes = remoteSize) }
                        }
                        if (entity.status != UploadStatus.READY) {
                            scheduler.enqueuePoll(entity.id, 0L, 0, replace = true)
                        }
                    }
                }
            }
        }
        return failure ?: ApiResult.Success(Unit)
    }

    override suspend fun testKeys(credentials: Credentials): KeyTestResult {
        val identifier = IdentifierGenerator.testItemFor(credentials.accessKey)
        val fileName = "paradox-test.txt"
        val content = "Paradox Uploader key test\n"
        val body = content.toRequestBody("text/plain".toMediaType())
        val request = UploadRequest(
            identifier = identifier,
            fileName = fileName,
            mimeType = "text/plain",
            sizeBytes = body.contentLength(),
            title = "Paradox Uploader key test",
            description = null,
            tags = null,
            collection = "opensource",
            mediatype = "data"
        )
        return when (val result = api.upload(credentials, request, body)) {
            is ApiResult.Failure -> KeyTestResult.Failure(result.kind, result.message)
            is ApiResult.Success -> {
                var cleaned = false
                repeat(3) { attempt ->
                    if (!cleaned) {
                        val deleted = api.deleteFile(credentials, identifier, fileName)
                        cleaned = deleted is ApiResult.Success ||
                            (deleted is ApiResult.Failure && deleted.kind == ErrorKind.NOT_FOUND)
                        if (!cleaned && attempt < 2) delay(2_000L)
                    }
                }
                KeyTestResult.Success(cleaned)
            }
        }
    }

    override suspend fun resumePending() {
        val current = settings.current()
        if (!current.uploadsPaused) {
            dao.getByStatus(listOf(UploadStatus.UPLOADING)).forEach {
                scheduler.enqueueUpload(it.id, current.wifiOnly, replace = false, chargingOnly = current.chargingOnly)
            }
        }
        dao.getByStatus(listOf(UploadStatus.PROCESSING, UploadStatus.DELAYED)).forEach {
            scheduler.enqueuePoll(it.id, 0L, 0, replace = false)
        }
    }

    override suspend fun pauseAll() {
        settings.update { it.copy(uploadsPaused = true) }
        scheduler.cancelAllUploads()
        dao.getByStatus(listOf(UploadStatus.UPLOADING)).forEach { tracker.clear(it.id) }
    }

    override suspend fun resumeAll() {
        settings.update { it.copy(uploadsPaused = false) }
        scheduleQueued()
    }

    override suspend fun applyUploadPolicy() {
        if (settings.current().uploadsPaused) return
        scheduleQueued()
    }

    /** Starts every queued file again with the current Wi-Fi / charging rules. */
    private suspend fun scheduleQueued() {
        val current = settings.current()
        dao.getByStatus(listOf(UploadStatus.UPLOADING)).forEach {
            scheduler.enqueueUpload(it.id, current.wifiOnly, replace = true, chargingOnly = current.chargingOnly)
        }
    }

    override suspend fun pushFolderMetadata(identifier: String, meta: FolderMeta): ApiResult<Unit> {
        val credentials = keys.get() ?: return ApiResult.Failure(ErrorKind.AUTH, null, "")
        return api.updateItemMetadata(credentials, identifier, meta)
    }

    private suspend fun mutate(id: Long, transform: UploadEntity.() -> UploadEntity) {
        dao.get(id)?.let { dao.update(it.transform()) }
    }
}

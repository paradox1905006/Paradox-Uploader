package com.dparadox.paradoxuploader

import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.data.local.UploadEntity
import com.dparadox.paradoxuploader.data.repository.UploadRepositoryImpl
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.model.EnqueueError
import com.dparadox.paradoxuploader.domain.model.ErrorCodes
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.model.IdentifierMode
import com.dparadox.paradoxuploader.domain.model.KeyTestResult
import com.dparadox.paradoxuploader.domain.model.PollOutcome
import com.dparadox.paradoxuploader.domain.model.SourceInfo
import com.dparadox.paradoxuploader.domain.model.UploadMeta
import com.dparadox.paradoxuploader.domain.model.UploadOutcome
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.KeyStore
import com.dparadox.paradoxuploader.domain.repository.RemoteFile
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.SourceFiles
import com.dparadox.paradoxuploader.domain.repository.UploadRequest
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.ProgressTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

private class FakeDao : UploadDao {
    val state = MutableStateFlow<List<UploadEntity>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<UploadEntity>> = state.map { list -> list.sortedByDescending { it.createdAt } }
    override suspend fun getAll() = state.value
    override suspend fun get(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun getByStatus(statuses: List<UploadStatus>) = state.value.filter { it.status in statuses }
    override suspend fun insert(entity: UploadEntity): Long {
        val id = nextId++
        state.value = state.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: UploadEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }
    override suspend fun delete(ids: List<Long>) {
        state.value = state.value.filterNot { it.id in ids }
    }
}

private class FakeApi : ArchiveService {
    val uploadResults = ArrayDeque<ApiResult<Unit>>()
    var uploadDefault: ApiResult<Unit> = ApiResult.Success(Unit)
    var metadata: ApiResult<List<RemoteFile>> = ApiResult.Success(emptyList())
    var downloadable: ApiResult<Boolean> = ApiResult.Success(false)
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var uploadCalls = 0
    var deleteCalls = 0
    val metadataUpdates = mutableListOf<Pair<String, FolderMeta>>()

    override suspend fun upload(credentials: Credentials, request: UploadRequest, body: RequestBody): ApiResult<Unit> {
        uploadCalls++
        return uploadResults.removeFirstOrNull() ?: uploadDefault
    }
    override suspend fun deleteFile(credentials: Credentials, identifier: String, fileName: String): ApiResult<Unit> {
        deleteCalls++
        return deleteResult
    }
    override suspend fun updateItemMetadata(credentials: Credentials, identifier: String, meta: FolderMeta): ApiResult<Unit> {
        metadataUpdates += identifier to meta
        return ApiResult.Success(Unit)
    }
    override suspend fun fetchMetadata(identifier: String) = metadata
    override suspend fun isDownloadable(identifier: String, fileName: String) = downloadable
}

private class FakeScheduler : WorkScheduler {
    val uploads = mutableListOf<Long>()
    val chargingFlags = mutableListOf<Boolean>()
    var cancelAllCalls = 0
    val polls = mutableListOf<Triple<Long, Long, Int>>()
    override fun enqueueUpload(id: Long, wifiOnly: Boolean, replace: Boolean, chargingOnly: Boolean) {
        uploads += id
        chargingFlags += chargingOnly
    }
    override fun cancelUpload(id: Long) = Unit
    override fun cancelAllUploads() { cancelAllCalls++ }
    override fun enqueuePoll(id: Long, delayMs: Long, slot: Int, replace: Boolean) { polls += Triple(id, delayMs, slot) }
    override fun cancelPoll(id: Long) = Unit
}

private class FakeSources : SourceFiles {
    val released = mutableListOf<String>()
    override fun describe(uri: String): SourceInfo? =
        if (uri.contains("missing")) null else SourceInfo("My/file.txt", 10, "text/plain")
    override fun open(uri: String): InputStream = ByteArrayInputStream(ByteArray(10))
    override fun retainAccess(uri: String) = uri
    override fun release(uri: String) { released += uri }
}

private class FakeKeys(var credentials: Credentials? = Credentials("ak", "sk")) : KeyStore {
    override fun get() = credentials
    override fun save(credentials: Credentials) { this.credentials = credentials }
    override fun clear() { credentials = null }
}

private class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = flow
    override suspend fun current() = flow.value
    override suspend fun update(block: (AppSettings) -> AppSettings) { flow.value = block(flow.value) }
}

private class TestClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMs() = now
}

class UploadRepositoryTest {
    private lateinit var dao: FakeDao
    private lateinit var api: FakeApi
    private lateinit var scheduler: FakeScheduler
    private lateinit var sources: FakeSources
    private lateinit var keys: FakeKeys
    private lateinit var settings: FakeSettings
    private lateinit var clock: TestClock
    private lateinit var repo: UploadRepositoryImpl

    @Before fun setUp() {
        dao = FakeDao(); api = FakeApi(); scheduler = FakeScheduler(); sources = FakeSources()
        keys = FakeKeys(); settings = FakeSettings(); clock = TestClock()
        repo = UploadRepositoryImpl(dao, api, keys, settings, scheduler, sources, ProgressTracker(), clock)
    }

    private suspend fun queueOne(): Long {
        repo.enqueue(listOf("content://doc/1"), UploadMeta())
        return dao.getAll().single().id
    }

    private suspend fun insertProcessing(startedAt: Long = clock.now): Long = dao.insert(
        UploadEntity(
            identifier = "paradox-abc12345", fileName = "file.txt", sizeBytes = 10, mimeType = "text/plain",
            createdAt = clock.now, status = UploadStatus.PROCESSING, processingStartedAt = startedAt
        )
    )

    @Test fun enqueueWithoutKeysIsRejected() = runTest {
        keys.clear()
        val result = repo.enqueue(listOf("content://doc/1"), UploadMeta())
        assertEquals(EnqueueError.NO_KEYS, result.error)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun enqueueCreatesUploadingRowAndSchedulesWork() = runTest {
        val result = repo.enqueue(listOf("content://doc/1", "content://missing"), UploadMeta(title = "T"))
        assertEquals(1, result.queued)
        assertEquals(1, result.skipped)
        val row = dao.getAll().single()
        assertEquals(UploadStatus.UPLOADING, row.status)
        assertEquals("My_file.txt", row.fileName)
        assertEquals("T", row.title)
        assertTrue(row.identifier.startsWith("paradox-"))
        assertEquals(listOf(row.id), scheduler.uploads)
    }

    @Test fun fixedModeUsesTheConfiguredIdentifier() = runTest {
        settings.flow.value = AppSettings(identifierMode = IdentifierMode.FIXED, fixedIdentifier = "my-fixed_item")
        queueOne()
        assertEquals("my-fixed_item", dao.getAll().single().identifier)
    }

    @Test fun fixedModeRejectsInvalidIdentifier() = runTest {
        settings.flow.value = AppSettings(identifierMode = IdentifierMode.FIXED, fixedIdentifier = "Bad Id")
        val result = repo.enqueue(listOf("content://doc/1"), UploadMeta())
        assertEquals(EnqueueError.INVALID_IDENTIFIER, result.error)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun successfulUploadMovesToProcessingAndStartsPolling() = runTest {
        val id = queueOne()
        assertEquals(UploadOutcome.Done, repo.runUpload(id) {})
        val row = dao.get(id)!!
        assertEquals(UploadStatus.PROCESSING, row.status)
        assertEquals(clock.now, row.processingStartedAt)
        assertNull(row.sourceUri)
        assertEquals(listOf(Triple(id, 10_000L, 0)), scheduler.polls)
        assertEquals(listOf("content://doc/1"), sources.released)
    }

    @Test fun badKeysFailTheUpload() = runTest {
        val id = queueOne()
        api.uploadDefault = ApiResult.Failure(ErrorKind.AUTH, 403, "")
        assertEquals(UploadOutcome.Failed(ErrorCodes.AUTH), repo.runUpload(id) {})
        val row = dao.get(id)!!
        assertEquals(UploadStatus.FAILED, row.status)
        assertEquals(ErrorCodes.AUTH, row.errorMessage)
    }

    @Test fun networkLossKeepsTheUploadQueued() = runTest {
        val id = queueOne()
        api.uploadDefault = ApiResult.Failure(ErrorKind.NETWORK, null, "offline")
        assertEquals(UploadOutcome.RetryLater, repo.runUpload(id) {})
        assertEquals(UploadStatus.UPLOADING, dao.get(id)!!.status)
    }

    @Test fun slowDownIsRetriedWithBackoff() = runTest {
        val id = queueOne()
        api.uploadResults.addLast(ApiResult.Failure(ErrorKind.SLOW_DOWN, 503, ""))
        api.uploadResults.addLast(ApiResult.Failure(ErrorKind.SLOW_DOWN, 503, ""))
        assertEquals(UploadOutcome.Done, repo.runUpload(id) {})
        assertEquals(3, api.uploadCalls)
        assertEquals(UploadStatus.PROCESSING, dao.get(id)!!.status)
    }

    @Test fun retryRequeuesAFailedUpload() = runTest {
        val id = queueOne()
        api.uploadDefault = ApiResult.Failure(ErrorKind.IDENTIFIER, 409, "")
        repo.runUpload(id) {}
        scheduler.uploads.clear()
        repo.retry(id)
        assertEquals(UploadStatus.UPLOADING, dao.get(id)!!.status)
        assertNull(dao.get(id)!!.errorMessage)
        assertEquals(listOf(id), scheduler.uploads)
    }

    @Test fun pollKeepsWaitingWhileTheFileIsMissing() = runTest {
        val id = insertProcessing()
        api.metadata = ApiResult.Success(emptyList())
        assertEquals(PollOutcome.Pending(10_000L), repo.runPoll(id))
        assertEquals(UploadStatus.PROCESSING, dao.get(id)!!.status)
    }

    // The /download/ HEAD check is unreliable for brand-new files, so being listed with the right size is enough.
    @Test fun pollIsReadyOnceListedWithTheRightSize() = runTest {
        val id = insertProcessing()
        api.metadata = ApiResult.Success(listOf(RemoteFile("file.txt", 10, null, null)))
        api.downloadable = ApiResult.Success(false)
        assertTrue(repo.runPoll(id) is PollOutcome.Ready)
    }

    @Test fun pollKeepsWaitingWhileTheListedSizeIsWrong() = runTest {
        val id = insertProcessing()
        api.metadata = ApiResult.Success(listOf(RemoteFile("file.txt", 3, null, null)))
        assertTrue(repo.runPoll(id) is PollOutcome.Pending)
    }

    @Test fun pollMarksTheFileReady() = runTest {
        val id = insertProcessing()
        api.metadata = ApiResult.Success(listOf(RemoteFile("file.txt", 10, null, null)))
        api.downloadable = ApiResult.Success(true)
        val outcome = repo.runPoll(id)
        assertTrue(outcome is PollOutcome.Ready)
        val row = dao.get(id)!!
        assertEquals(UploadStatus.READY, row.status)
        assertEquals("https://archive.org/download/paradox-abc12345/file.txt", row.directUrl)
        assertEquals(PollOutcome.Gone, repo.runPoll(id))
    }

    @Test fun pollNeverFailsOnNetworkErrors() = runTest {
        val id = insertProcessing()
        api.metadata = ApiResult.Failure(ErrorKind.NETWORK, null, "offline")
        assertEquals(PollOutcome.Pending(10_000L), repo.runPoll(id))
        assertEquals(UploadStatus.PROCESSING, dao.get(id)!!.status)
    }

    @Test fun pollBacksOffAndEventuallyReportsDelayed() = runTest {
        val id = insertProcessing(startedAt = clock.now)
        clock.now += 90_000
        assertEquals(PollOutcome.Pending(30_000L), repo.runPoll(id))
        clock.now += 20 * 60_000
        assertEquals(PollOutcome.Pending(120_000L), repo.runPoll(id))
        clock.now += 61 * 60_000
        // After the normal schedule the file shows as DELAYED but is still re-checked every 30 minutes.
        assertEquals(PollOutcome.Pending(30 * 60_000L), repo.runPoll(id))
        assertEquals(UploadStatus.DELAYED, dao.get(id)!!.status)
    }

    @Test fun delayedFileCanStillBecomeReady() = runTest {
        val id = insertProcessing(startedAt = clock.now)
        clock.now += 2 * 60 * 60_000
        repo.runPoll(id)
        assertEquals(UploadStatus.DELAYED, dao.get(id)!!.status)
        api.metadata = ApiResult.Success(listOf(RemoteFile("file.txt", 10, null, null)))
        api.downloadable = ApiResult.Success(true)
        assertTrue(repo.runPoll(id) is PollOutcome.Ready)
        assertEquals(UploadStatus.READY, dao.get(id)!!.status)
    }

    @Test fun deleteRemovesTheRowAfterTheRemoteDelete() = runTest {
        val id = insertProcessing()
        assertTrue(repo.delete(id) is ApiResult.Success)
        assertEquals(1, api.deleteCalls)
        assertNull(dao.get(id))
    }

    @Test fun deleteTreatsMissingRemoteFileAsDeleted() = runTest {
        val id = insertProcessing()
        api.deleteResult = ApiResult.Failure(ErrorKind.NOT_FOUND, 404, "")
        assertTrue(repo.delete(id) is ApiResult.Success)
        assertNull(dao.get(id))
    }

    @Test fun failedRemoteDeleteKeepsTheRow() = runTest {
        val id = insertProcessing()
        api.deleteResult = ApiResult.Failure(ErrorKind.NETWORK, null, "offline")
        assertTrue(repo.delete(id) is ApiResult.Failure)
        assertNotNull(dao.get(id))
    }

    @Test fun refreshUpdatesSizesAndRechecksPendingFiles() = runTest {
        val readyId = dao.insert(
            UploadEntity(
                identifier = "paradox-abc12345", fileName = "file.txt", sizeBytes = 1, mimeType = "text/plain",
                createdAt = clock.now, status = UploadStatus.READY, processingStartedAt = clock.now
            )
        )
        val pendingId = insertProcessing()
        api.metadata = ApiResult.Success(listOf(RemoteFile("file.txt", 4096, null, null)))
        assertTrue(repo.refresh() is ApiResult.Success)
        assertEquals(4096L, dao.get(readyId)!!.sizeBytes)
        assertEquals(4096L, dao.get(pendingId)!!.sizeBytes)
        assertTrue(scheduler.polls.any { it.first == pendingId })
        assertTrue(scheduler.polls.none { it.first == readyId })
    }

    @Test fun keyTestSucceedsAndCleansUp() = runTest {
        val result = repo.testKeys(Credentials("a", "b"))
        assertEquals(KeyTestResult.Success(cleanedUp = true), result)
        assertEquals(1, api.uploadCalls)
        assertEquals(1, api.deleteCalls)
    }

    @Test fun keyTestReportsBadKeys() = runTest {
        api.uploadDefault = ApiResult.Failure(ErrorKind.AUTH, 403, "denied")
        val result = repo.testKeys(Credentials("a", "b"))
        assertEquals(KeyTestResult.Failure(ErrorKind.AUTH, "denied"), result)
        assertEquals(0, api.deleteCalls)
    }

    @Test fun resumePendingReschedulesTheQueue() = runTest {
        val uploadId = queueOne()
        val pollId = insertProcessing()
        scheduler.uploads.clear()
        repo.resumePending()
        assertEquals(listOf(uploadId), scheduler.uploads)
        assertTrue(scheduler.polls.any { it.first == pollId })
    }

    @Test fun resumePendingAlsoRechecksDelayedFiles() = runTest {
        val id = insertProcessing(startedAt = clock.now)
        clock.now += 2 * 60 * 60_000
        repo.runPoll(id)
        scheduler.polls.clear()
        repo.resumePending()
        assertTrue(scheduler.polls.any { it.first == id })
    }

    @Test fun theSameSourceIsNotQueuedTwice() = runTest {
        repo.enqueue(listOf("content://doc/1"), UploadMeta())
        val second = repo.enqueue(listOf("content://doc/1"), UploadMeta())
        assertEquals(0, second.queued)
        assertEquals(1, second.skipped)
        assertEquals(1, dao.getAll().size)
    }

    @Test fun aDifferentFileWithTheSameNameGoesToItsOwnItem() = runTest {
        repo.enqueue(listOf("content://doc/1"), UploadMeta())
        val second = repo.enqueue(listOf("content://doc/2"), UploadMeta())
        assertEquals(1, second.queued)
        assertEquals(2, dao.getAll().map { it.identifier }.distinct().size)
    }

    @Test fun sameNameAndSizeIntoTheSameFixedItemIsSkipped() = runTest {
        settings.flow.value = AppSettings(identifierMode = IdentifierMode.FIXED, fixedIdentifier = "my-fixed_item")
        repo.enqueue(listOf("content://doc/1"), UploadMeta())
        val second = repo.enqueue(listOf("content://doc/2"), UploadMeta())
        assertEquals(0, second.queued)
        assertEquals(1, dao.getAll().size)
    }

    private val cc = "https://creativecommons.org/licenses/by/4.0/"

    private fun folder() = Folder(
        id = 7, name = "Trip", identifier = "paradox-trip-abc", createdAt = 0L,
        title = "Trip 2026", description = "Photos", tags = "trip, 2026", licenseUrl = cc
    )

    @Test fun folderDefaultsApplyToEveryQueuedFile() = runTest {
        repo.enqueue(listOf("content://doc/1"), UploadMeta(folder = folder()))
        val row = dao.getAll().single()
        assertEquals("Trip 2026", row.title)
        assertEquals("Photos", row.description)
        assertEquals("trip, 2026", row.tags)
        assertEquals(cc, row.licenseUrl)
        assertEquals("paradox-trip-abc", row.identifier)
    }

    @Test fun valuesTypedForTheUploadWinOverFolderDefaults() = runTest {
        repo.enqueue(listOf("content://doc/1"), UploadMeta(title = "Mine", tags = "x", folder = folder()))
        val row = dao.getAll().single()
        assertEquals("Mine", row.title)
        assertEquals("x", row.tags)
        assertEquals("Photos", row.description)
    }

    @Test fun folderWithoutTitleFallsBackToItsName() = runTest {
        repo.enqueue(listOf("content://doc/1"), UploadMeta(folder = folder().copy(title = null, licenseUrl = null)))
        val row = dao.getAll().single()
        assertEquals("Trip", row.title)
        assertNull(row.licenseUrl)
    }

    @Test fun pauseAllStopsJobsAndKeepsNewFilesQueued() = runTest {
        queueOne()
        scheduler.uploads.clear()
        repo.pauseAll()
        assertTrue(settings.flow.value.uploadsPaused)
        assertEquals(1, scheduler.cancelAllCalls)
        repo.enqueue(listOf("content://doc/2"), UploadMeta())
        assertEquals(2, dao.getAll().count { it.status == UploadStatus.UPLOADING })
        assertTrue(scheduler.uploads.isEmpty())
    }

    @Test fun resumeAllSchedulesEveryQueuedFile() = runTest {
        queueOne()
        repo.pauseAll()
        repo.enqueue(listOf("content://doc/2"), UploadMeta())
        scheduler.uploads.clear()
        repo.resumeAll()
        assertTrue(!settings.flow.value.uploadsPaused)
        assertEquals(2, scheduler.uploads.size)
    }

    @Test fun resumePendingDoesNotStartUploadsWhilePaused() = runTest {
        queueOne()
        val pollId = insertProcessing()
        repo.pauseAll()
        scheduler.uploads.clear()
        repo.resumePending()
        assertTrue(scheduler.uploads.isEmpty())
        assertTrue(scheduler.polls.any { it.first == pollId })
    }

    @Test fun retryWhilePausedLeavesTheFileQueued() = runTest {
        val id = queueOne()
        api.uploadDefault = ApiResult.Failure(ErrorKind.AUTH, 403, "")
        repo.runUpload(id) {}
        repo.pauseAll()
        scheduler.uploads.clear()
        repo.retry(id)
        assertEquals(UploadStatus.UPLOADING, dao.get(id)!!.status)
        assertTrue(scheduler.uploads.isEmpty())
    }

    @Test fun chargingOnlyIsPassedToTheScheduler() = runTest {
        settings.flow.value = AppSettings(chargingOnly = true)
        queueOne()
        assertEquals(listOf(true), scheduler.chargingFlags)
    }

    @Test fun changingThePolicyReschedulesQueuedFiles() = runTest {
        queueOne()
        scheduler.uploads.clear()
        scheduler.chargingFlags.clear()
        settings.flow.value = AppSettings(chargingOnly = true)
        repo.applyUploadPolicy()
        assertEquals(1, scheduler.uploads.size)
        assertEquals(listOf(true), scheduler.chargingFlags)
    }

    @Test fun pushingFolderMetadataNeedsKeys() = runTest {
        keys.clear()
        val result = repo.pushFolderMetadata("paradox-trip-abc", FolderMeta(title = "T"))
        assertTrue(result is ApiResult.Failure && result.kind == ErrorKind.AUTH)
        assertTrue(api.metadataUpdates.isEmpty())
    }

    @Test fun pushingFolderMetadataCallsTheApi() = runTest {
        val meta = FolderMeta(title = "T", licenseUrl = cc)
        assertTrue(repo.pushFolderMetadata("paradox-trip-abc", meta) is ApiResult.Success)
        assertEquals(listOf("paradox-trip-abc" to meta), api.metadataUpdates)
    }
}

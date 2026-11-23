package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import android.net.Uri
import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.ForumTopic
import com.dparadox.paradoxuploader.domain.telegram.PickedFile
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import com.dparadox.paradoxuploader.domain.telegram.UploadController
import com.dparadox.paradoxuploader.domain.telegram.UploadJob
import com.dparadox.paradoxuploader.domain.telegram.UploadRepository
import com.dparadox.paradoxuploader.domain.telegram.UploadSelection
import com.dparadox.paradoxuploader.domain.telegram.UploadStatus
import com.dparadox.paradoxuploader.domain.telegram.toTgError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when a file cannot be staged for upload (no space, unreadable URI). Shown to the user as-is. */
class StorageException(message: String) : Exception(message)

/**
 * Single-consumer upload queue. Files upload one at a time, in order, so Telegram is never
 * flooded and progress stays meaningful. Runs in the application scope; [UploadService] keeps the process alive.
 */
@Singleton
class UploadControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: UploadRepository,
    @ApplicationScope private val scope: CoroutineScope
) : UploadController {

    private val _selection = MutableStateFlow(UploadSelection(null, null))
    override val selection: StateFlow<UploadSelection> = _selection

    private val _recent = MutableStateFlow<List<ChatDestination>>(emptyList())
    override val recent: StateFlow<List<ChatDestination>> = _recent

    private val _jobs = MutableStateFlow<List<UploadJob>>(emptyList())
    override val jobs: StateFlow<List<UploadJob>> = _jobs

    private val _paused = MutableStateFlow(false)
    override val paused: StateFlow<Boolean> = _paused

    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var currentJobId: String? = null
    @Volatile private var currentJob: Job? = null

    init {
        scope.launch { worker() }
    }

    override fun selectDestination(destination: ChatDestination) {
        _selection.value = UploadSelection(destination, null)
        _recent.update { list -> (listOf(destination) + list.filterNot { it.chatId == destination.chatId }).take(5) }
    }

    override fun selectTopic(topic: ForumTopic?) = _selection.update { it.copy(topic = topic) }

    override fun enqueue(files: List<PickedFile>, caption: String) {
        val sel = _selection.value
        val dest = sel.destination ?: return
        val now = System.currentTimeMillis()
        val newJobs = files.map { f ->
            UploadJob(
                id = UUID.randomUUID().toString(),
                uri = f.uri,
                fileName = f.name,
                mimeType = f.mime,
                sizeBytes = f.size,
                caption = caption,
                chatId = dest.chatId,
                chatTitle = dest.title,
                topicId = sel.topic?.threadId,
                topicName = sel.topic?.name,
                status = UploadStatus.WAITING,
                createdAt = now
            )
        }
        _jobs.update { it + newJobs }
        UploadService.start(context)
        wake.trySend(Unit)
    }

    override fun setPaused(paused: Boolean) {
        _paused.value = paused
        if (!paused) wake.trySend(Unit)
    }

    override fun retry(id: String) {
        mutate(id) {
            it.copy(
                status = UploadStatus.WAITING,
                uploadedBytes = 0,
                speedBps = 0.0,
                etaSeconds = null,
                tdFileId = null,
                error = null
            )
        }
        UploadService.start(context)
        wake.trySend(Unit)
    }

    override fun cancel(id: String) {
        if (currentJobId == id) {
            currentJob?.cancel()
            return
        }
        mutate(id) { if (it.status == UploadStatus.WAITING) it.copy(status = UploadStatus.CANCELLED) else it }
    }

    override fun clearFinished() {
        _jobs.update { list ->
            list.filter { it.status == UploadStatus.WAITING || it.status == UploadStatus.UPLOADING }
        }
    }

    private suspend fun worker() {
        while (true) {
            wake.receive()
            while (!_paused.value) {
                val next = _jobs.value.firstOrNull { it.status == UploadStatus.WAITING } ?: break
                runJob(next)
            }
        }
    }

    private suspend fun runJob(job: UploadJob) = coroutineScope {
        val task = launch {
            mutate(job.id) {
                it.copy(status = UploadStatus.UPLOADING, uploadedBytes = 0, speedBps = 0.0, etaSeconds = null, error = null)
            }
            val sampler = Sampler()
            var staged: File? = null
            try {
                val file = copyToStaging(job)
                staged = file
                val result = repo.sendFile(
                    chatId = job.chatId,
                    topicId = job.topicId,
                    path = file.absolutePath,
                    caption = job.caption,
                    onFileId = { id -> mutate(job.id) { it.copy(tdFileId = id) } },
                    onProgress = { uploaded, total ->
                        val (speed, eta) = sampler.sample(uploaded, total)
                        mutate(job.id) {
                            it.copy(
                                uploadedBytes = uploaded,
                                sizeBytes = if (total > 0) total else it.sizeBytes,
                                speedBps = speed,
                                etaSeconds = eta
                            )
                        }
                    }
                )
                when (result) {
                    is TgResult.Success -> mutate(job.id) {
                        it.copy(
                            status = UploadStatus.COMPLETED,
                            uploadedBytes = it.sizeBytes,
                            speedBps = 0.0,
                            etaSeconds = null,
                            messageId = result.value.messageId,
                            link = result.value.link
                        )
                    }
                    is TgResult.Failure -> mutate(job.id) {
                        it.copy(status = UploadStatus.FAILED, error = result.error, speedBps = 0.0, etaSeconds = null)
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val fileId = _jobs.value.firstOrNull { it.id == job.id }?.tdFileId
                    if (fileId != null) repo.cancelUpload(fileId)
                    mutate(job.id) { it.copy(status = UploadStatus.CANCELLED, speedBps = 0.0, etaSeconds = null) }
                }
                throw e
            } catch (e: Throwable) {
                val err: TgError = if (e is StorageException) TgError.Unknown(e.message) else e.toTgError()
                mutate(job.id) { it.copy(status = UploadStatus.FAILED, error = err, speedBps = 0.0, etaSeconds = null) }
            } finally {
                // Remove the whole per-job folder: a cancel during the copy left a partial file behind.
                staged?.delete()
                File(File(context.cacheDir, "upload_staging"), job.id).deleteRecursively()
            }
        }
        currentJobId = job.id
        currentJob = task
        task.join()
        currentJob = null
        currentJobId = null
    }

    /** Copies the picked URI into app cache, since TDLib uploads from a local path. */
    private suspend fun copyToStaging(job: UploadJob): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "upload_staging").apply { mkdirs() }
        if (job.sizeBytes > 0 && dir.usableSpace < job.sizeBytes + SPACE_MARGIN) {
            throw StorageException("Not enough free storage to stage ${job.fileName}")
        }
        // Telegram shows the file name taken from the local path, so keep the original name
        // inside a per-job folder instead of prefixing it with the job id.
        val jobDir = File(dir, job.id).apply { mkdirs() }
        val safeName = job.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val dst = File(jobDir, safeName)
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(job.uri))
                ?: throw StorageException("Cannot open ${job.fileName}. Pick it again.")
            input.use { i ->
                dst.outputStream().use { o ->
                    val buf = ByteArray(COPY_BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                    }
                }
            }
        } catch (e: IOException) {
            dst.delete()
            throw StorageException("Could not read ${job.fileName}")
        } catch (e: SecurityException) {
            dst.delete()
            throw StorageException("Permission to read ${job.fileName} was lost. Pick it again.")
        }
        dst
    }

    private fun mutate(id: String, f: (UploadJob) -> UploadJob) {
        _jobs.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    /** Smooths speed with an exponential moving average and estimates time remaining from it. */
    private class Sampler {
        private var lastT = System.nanoTime()
        private var lastB = 0L
        private var ema = 0.0

        fun sample(bytes: Long, total: Long): Pair<Double, Long?> {
            val now = System.nanoTime()
            val dt = (now - lastT) / 1e9
            if (dt >= 0.5) {
                val inst = (bytes - lastB) / dt
                ema = if (ema == 0.0) inst else 0.3 * inst + 0.7 * ema
                lastT = now
                lastB = bytes
            }
            val eta = if (ema > 1.0 && total > bytes) ((total - bytes) / ema).toLong() else null
            return ema to eta
        }
    }

    private companion object {
        const val SPACE_MARGIN = 64L * 1024 * 1024
        const val COPY_BUFFER = 1 shl 16
    }
}

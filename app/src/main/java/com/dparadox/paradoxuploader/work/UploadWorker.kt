package com.dparadox.paradoxuploader.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dparadox.paradoxuploader.domain.model.ErrorCodes
import com.dparadox.paradoxuploader.domain.model.UploadOutcome
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/** Uploads one file in the foreground (dataSync) so it keeps running with the screen off. */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UploadRepository,
    private val notifier: Notifier,
    private val gate: ConcurrencyGate,
    private val settings: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_UPLOAD_ID, -1L)
        val item = repository.getItem(id) ?: return Result.success()
        if (item.status != UploadStatus.UPLOADING) return Result.success()
        val current = settings.current()
        // Pause all: the file stays queued and starts again when the user resumes.
        if (current.uploadsPaused) return Result.success()
        val limit = current.concurrentUploads

        return gate.withSlot(limit) {
            try {
                setForeground(notifier.foregroundInfo(item, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not allowed to start a foreground service right now; keep uploading anyway.
            }
            val outcome = gate.withItemCreation(item.identifier) {
                repository.runUpload(id) { progress -> notifier.updateProgress(item, progress) }
            }
            when (outcome) {
                UploadOutcome.Done -> Result.success()
                UploadOutcome.RetryLater -> {
                    if (runAttemptCount >= MAX_ATTEMPTS) {
                        repository.markFailed(id, ErrorCodes.NETWORK)
                        notifier.showFailure(item, ErrorCodes.NETWORK)
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
                is UploadOutcome.Failed -> {
                    notifier.showFailure(item, outcome.code)
                    Result.success()
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

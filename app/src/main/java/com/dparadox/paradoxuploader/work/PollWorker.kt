package com.dparadox.paradoxuploader.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dparadox.paradoxuploader.domain.model.PollOutcome
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import com.dparadox.paradoxuploader.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One polling step: checks whether the file is downloadable. Network errors never fail the job;
 * the next step is simply scheduled according to the backoff schedule.
 */
@HiltWorker
class PollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UploadRepository,
    private val scheduler: WorkScheduler,
    private val notifier: Notifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_UPLOAD_ID, -1L)
        val slot = inputData.getInt(KEY_POLL_SLOT, 0)
        when (val outcome = repository.runPoll(id)) {
            is PollOutcome.Ready -> notifier.showLinkReady(outcome.item)
            is PollOutcome.Pending -> scheduler.enqueuePoll(id, outcome.nextDelayMs, 1 - slot, replace = true)
            PollOutcome.Delayed, PollOutcome.Gone -> Unit
        }
        return Result.success()
    }
}

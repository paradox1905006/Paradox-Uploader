package com.dparadox.paradoxuploader.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val KEY_UPLOAD_ID = "upload_id"
const val KEY_POLL_SLOT = "poll_slot"

/**
 * Upload work is unique per file ("upload-<id>"). Polling alternates between two unique names
 * ("poll-<id>-0" / "poll-<id>-1"): each run re-enqueues the other slot, so a worker never
 * replaces itself and every run stays short.
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun enqueueUpload(id: Long, wifiOnly: Boolean, replace: Boolean, chargingOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(KEY_UPLOAD_ID to id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("upload")
            .build()
        workManager.enqueueUniqueWork(
            "upload-$id",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelUpload(id: Long) {
        workManager.cancelUniqueWork("upload-$id")
    }

    override fun cancelAllUploads() {
        workManager.cancelAllWorkByTag("upload")
    }

    override fun enqueuePoll(id: Long, delayMs: Long, slot: Int, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<PollWorker>()
            .setInputData(workDataOf(KEY_UPLOAD_ID to id, KEY_POLL_SLOT to slot))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag("poll")
            .build()
        workManager.enqueueUniqueWork(
            "poll-$id-$slot",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelPoll(id: Long) {
        workManager.cancelUniqueWork("poll-$id-0")
        workManager.cancelUniqueWork("poll-$id-1")
    }
}

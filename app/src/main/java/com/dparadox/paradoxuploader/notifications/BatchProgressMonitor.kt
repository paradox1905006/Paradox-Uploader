package com.dparadox.paradoxuploader.notifications

import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadProgress
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.util.ProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the upload queue and keeps ONE overall progress notification while two or more files
 * are uploading (for example a whole folder). A single file only gets its own notification.
 */
@Singleton
class BatchProgressMonitor @Inject constructor(
    private val repository: UploadRepository,
    private val tracker: ProgressTracker,
    private val notifier: Notifier,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var started = false
    private var shown = false

    /** Files seen uploading in the current batch: id -> size in bytes. */
    private val seen = HashMap<Long, Long>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(repository.observeItems(), tracker.progress) { items, progress -> items to progress }
                .conflate()
                .collect { (items, progress) ->
                    render(items, progress)
                    delay(UPDATE_INTERVAL_MS)
                }
        }
    }

    private fun render(items: List<UploadItem>, progress: Map<Long, UploadProgress>) {
        val byId = items.associateBy { it.id }
        seen.keys.retainAll(byId.keys)
        val active = items.filter { it.status == UploadStatus.UPLOADING }
        active.forEach { if (it.id !in seen) seen[it.id] = it.sizeBytes.coerceAtLeast(0L) }

        if (active.isEmpty()) {
            if (seen.size >= 2) {
                val failed = seen.keys.count { byId[it]?.status == UploadStatus.FAILED }
                notifier.showBatchDone(seen.size - failed, failed)
            }
            seen.clear()
            hide()
            return
        }
        if (seen.size < 2) {
            hide()
            return
        }

        var totalBytes = 0L
        var sentBytes = 0L
        var speed = 0L
        var done = 0
        for ((id, size) in seen) {
            totalBytes += size
            if (byId[id]?.status == UploadStatus.UPLOADING) {
                val p = progress[id]
                if (p != null) {
                    sentBytes += p.bytesSent.coerceAtMost(size)
                    speed += p.speedBps
                }
            } else {
                sentBytes += size
                done++
            }
        }
        val percent = if (totalBytes > 0) (sentBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
        val eta = if (speed > 0) (totalBytes - sentBytes).coerceAtLeast(0L) / speed else -1L
        notifier.showBatchProgress(done, seen.size, percent, speed, eta)
        shown = true
    }

    private fun hide() {
        if (shown) {
            notifier.clearBatchProgress()
            shown = false
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}

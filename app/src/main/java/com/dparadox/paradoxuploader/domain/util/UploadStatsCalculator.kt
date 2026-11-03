package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStats
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import java.time.Instant
import java.time.ZoneId

/** Builds the numbers of the stats card from the history. Files that reached archive.org are counted. */
object UploadStatsCalculator {
    private val UPLOADED = setOf(UploadStatus.PROCESSING, UploadStatus.READY, UploadStatus.DELAYED)

    fun from(items: List<UploadItem>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): UploadStats {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var files = 0
        var bytes = 0L
        var monthFiles = 0
        var monthBytes = 0L
        var todayFiles = 0
        var todayBytes = 0L
        var failed = 0
        for (item in items) {
            if (item.status == UploadStatus.FAILED) failed++
            if (item.status !in UPLOADED) continue
            val size = item.sizeBytes.coerceAtLeast(0L)
            files++
            bytes += size
            val day = Instant.ofEpochMilli(item.createdAt).atZone(zone).toLocalDate()
            if (day.year == today.year && day.month == today.month) {
                monthFiles++
                monthBytes += size
            }
            if (day == today) {
                todayFiles++
                todayBytes += size
            }
        }
        return UploadStats(files, bytes, monthFiles, monthBytes, todayFiles, todayBytes, failed)
    }
}

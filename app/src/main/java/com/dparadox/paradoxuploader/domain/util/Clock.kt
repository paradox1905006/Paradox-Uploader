package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadProgress
import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun nowMs(): Long
}

@Singleton
class DeviceClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/** Turns raw byte counts into throttled [UploadProgress] values with smoothed speed and ETA. */
class SpeedMeter(private val clock: Clock, private val minIntervalMs: Long = 500L) {
    private var lastEmitMs = 0L
    private var lastBytes = 0L
    private var lastMs = clock.nowMs()
    private var ema = 0.0

    fun update(sent: Long, total: Long): UploadProgress? {
        val now = clock.nowMs()
        val done = total > 0 && sent >= total
        if (!done && now - lastEmitMs < minIntervalMs) return null
        val dt = (now - lastMs).coerceAtLeast(1L)
        val instant = (sent - lastBytes) * 1000.0 / dt
        ema = if (ema == 0.0) instant else 0.3 * instant + 0.7 * ema
        lastBytes = sent
        lastMs = now
        lastEmitMs = now
        val remaining = if (total > 0) total - sent else -1L
        val eta = if (ema > 1.0 && remaining >= 0) (remaining / ema).toLong() else -1L
        return UploadProgress(sent, total, ema.toLong(), eta)
    }
}

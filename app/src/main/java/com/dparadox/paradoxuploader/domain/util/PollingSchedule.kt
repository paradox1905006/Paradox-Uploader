package com.dparadox.paradoxuploader.domain.util

/**
 * Poll cadence after an upload: every 10 s for the first minute, every 30 s up to
 * 10 minutes, every 2 minutes up to 1 hour. Afterwards `null` means "give up -> DELAYED".
 */
object PollingSchedule {
    const val FIRST_PHASE_MS = 60_000L
    const val SECOND_PHASE_MS = 10 * 60_000L
    const val THIRD_PHASE_MS = 60 * 60_000L
    /** Rechecks after the normal schedule has run out, so a late-listed file still gets picked up. */
    const val SLOW_RECHECK_MS = 30 * 60_000L

    fun nextDelayMs(elapsedMs: Long): Long? = when {
        elapsedMs < FIRST_PHASE_MS -> 10_000L
        elapsedMs < SECOND_PHASE_MS -> 30_000L
        elapsedMs < THIRD_PHASE_MS -> 120_000L
        else -> null
    }
}

/** Exponential backoff for HTTP 503 SlowDown. */
object RetryPolicy {
    const val MAX_SLOWDOWN_RETRIES = 5

    fun delayMs(attempt: Int, baseMs: Long = 2_000L, capMs: Long = 60_000L): Long {
        val shift = attempt.coerceIn(0, 20)
        return minOf(baseMs shl shift, capMs)
    }
}

package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadStatus
import com.dparadox.paradoxuploader.domain.model.UploadStatus.*

/** Allowed transitions: UPLOADING -> PROCESSING -> READY / DELAYED / FAILED. */
object StatusMachine {
    private val allowed: Map<UploadStatus, Set<UploadStatus>> = mapOf(
        UPLOADING to setOf(PROCESSING, FAILED),
        PROCESSING to setOf(READY, DELAYED, FAILED),
        DELAYED to setOf(PROCESSING, READY, FAILED),
        FAILED to setOf(UPLOADING),
        READY to emptySet()
    )

    fun canTransition(from: UploadStatus, to: UploadStatus): Boolean = to in allowed.getValue(from)

    fun transition(from: UploadStatus, to: UploadStatus): UploadStatus {
        check(canTransition(from, to)) { "Illegal status transition $from -> $to" }
        return to
    }
}

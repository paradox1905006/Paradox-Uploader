package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus

/**
 * Finds files that were already uploaded. A file counts as a duplicate when an earlier upload
 * (not a failed one) has the same name and the same size. Reading whole files to hash them would
 * be far too slow for multi-GB uploads, so name + size is used.
 */
object DuplicateChecker {
    fun find(existing: List<UploadItem>, fileName: String, sizeBytes: Long): UploadItem? {
        val clean = ArchiveUrls.sanitizeFileName(fileName)
        return existing.firstOrNull {
            it.status != UploadStatus.FAILED &&
                it.sizeBytes == sizeBytes &&
                it.fileName.equals(clean, ignoreCase = true)
        }
    }
}

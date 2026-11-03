package com.dparadox.paradoxuploader.data.repository

import com.dparadox.paradoxuploader.data.local.EmptyItemStore
import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.CleanupScan
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.CleanupRepository
import com.dparadox.paradoxuploader.domain.util.RemoteFiles
import javax.inject.Inject
import javax.inject.Singleton

/**
 * archive.org (IAS3) lets an app delete files but not the item itself. So the app only finds the
 * items that have no files left; removing them is done on the website (My uploads -> Remove items).
 */
@Singleton
class CleanupRepositoryImpl @Inject constructor(
    private val store: EmptyItemStore,
    private val uploads: UploadDao,
    private val api: ArchiveService
) : CleanupRepository {

    override suspend fun onFileDeleted(identifier: String): Boolean {
        if (uploads.getAll().any { it.identifier == identifier }) return false
        store.add(identifier)
        return true
    }

    override suspend fun markPossiblyEmpty(identifier: String) {
        store.add(identifier)
    }

    override suspend fun scanEmptyItems(): CleanupScan {
        val empty = mutableListOf<String>()
        var failed = 0
        val inUse = uploads.getAll().map { it.identifier }.toSet()
        for (identifier in store.all().sorted()) {
            if (identifier in inUse) {
                store.remove(identifier)
                continue
            }
            when (val result = api.fetchMetadata(identifier)) {
                is ApiResult.Failure -> {
                    if (result.kind == ErrorKind.NOT_FOUND) store.remove(identifier) else failed++
                }
                is ApiResult.Success -> {
                    if (result.value.any { RemoteFiles.isOriginal(it) }) store.remove(identifier) else empty.add(identifier)
                }
            }
        }
        return CleanupScan(empty, failed)
    }

    override suspend fun dismiss(identifier: String) {
        store.remove(identifier)
    }

    override suspend fun dismissAll() {
        store.clear()
    }
}

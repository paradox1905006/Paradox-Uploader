package com.dparadox.paradoxuploader.data.repository

import com.dparadox.paradoxuploader.data.local.FolderDao
import com.dparadox.paradoxuploader.data.local.FolderEntity
import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.IdentifierGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folders: FolderDao,
    private val uploads: UploadDao,
    private val clock: Clock
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        combine(folders.observeAll(), uploads.observeAll()) { folderList, uploadList ->
            val counts = uploadList.groupingBy { it.folderId }.eachCount()
            folderList.map { it.toFolder(counts[it.id] ?: 0) }
        }

    override suspend fun get(id: Long): Folder? = folders.get(id)?.toFolder()

    override suspend fun findByName(name: String): Folder? = folders.findByName(name.trim())?.toFolder()

    override suspend fun create(name: String, meta: FolderMeta): Folder? {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        if (clean.isEmpty()) return null
        repeat(5) {
            val identifier = IdentifierGenerator.forFolder(clean)
            if (folders.findByIdentifier(identifier) == null) {
                val entity = FolderEntity(
                    name = clean,
                    identifier = identifier,
                    createdAt = clock.nowMs(),
                    title = meta.title.clean(),
                    description = meta.description.clean(),
                    tags = meta.tags.clean(),
                    licenseUrl = meta.licenseUrl.clean()
                )
                val id = folders.insert(entity)
                return entity.copy(id = id).toFolder()
            }
        }
        return null
    }

    override suspend fun update(id: Long, name: String, meta: FolderMeta): Folder? {
        val current = folders.get(id) ?: return null
        val updated = current.copy(
            name = name.trim().take(MAX_NAME_LENGTH).ifEmpty { current.name },
            title = meta.title.clean(),
            description = meta.description.clean(),
            tags = meta.tags.clean(),
            licenseUrl = meta.licenseUrl.clean()
        )
        folders.update(updated)
        return updated.toFolder()
    }

    override suspend fun delete(id: Long) {
        folders.detachUploads(id)
        folders.delete(id)
    }

    override suspend fun deleteIfEmpty(id: Long): Boolean {
        if (folders.get(id) == null) return false
        if (uploads.getAll().any { it.folderId == id }) return false
        delete(id)
        return true
    }

    override suspend fun removeEmpty(minAgeMs: Long): Int {
        val used = uploads.getAll().mapNotNull { it.folderId }.toSet()
        val cutoff = clock.nowMs() - minAgeMs
        var removed = 0
        for (folder in folders.getAll()) {
            if (folder.id !in used && folder.createdAt <= cutoff) {
                folders.delete(folder.id)
                removed++
            }
        }
        return removed
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun FolderEntity.toFolder(fileCount: Int = 0) = Folder(
        id = id,
        name = name,
        identifier = identifier,
        createdAt = createdAt,
        fileCount = fileCount,
        title = title,
        description = description,
        tags = tags,
        licenseUrl = licenseUrl
    )

    private companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

package com.dparadox.paradoxuploader.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.dparadox.paradoxuploader.domain.model.SourceInfo
import com.dparadox.paradoxuploader.domain.model.TreeListing
import com.dparadox.paradoxuploader.domain.repository.SourceFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads upload sources. SAF uris get a persistable permission when possible; uris without one
 * (typical for the share sheet) are copied to an app-private staging directory.
 */
@Singleton
class AndroidSourceFiles @Inject constructor(
    @ApplicationContext private val context: Context
) : SourceFiles {

    private val resolver get() = context.contentResolver
    private val stagingDir: File get() = File(context.filesDir, "staging").apply { mkdirs() }

    override fun describe(uri: String): SourceInfo? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            val file = File(parsed.path ?: return null)
            if (!file.exists()) return null
            return SourceInfo(file.name, file.length(), guessMime(file.name))
        }
        var name: String? = null
        var size = -1L
        try {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
            if (size < 0) {
                size = resolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: -1L
            }
        } catch (e: Exception) {
            return null
        }
        val resolvedName = name ?: parsed.lastPathSegment ?: return null
        val mime = resolver.getType(parsed) ?: guessMime(resolvedName)
        return SourceInfo(resolvedName, size, mime)
    }

    override fun open(uri: String): InputStream {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "file") {
            FileInputStream(File(parsed.path ?: throw FileNotFoundException(uri)))
        } else {
            resolver.openInputStream(parsed) ?: throw FileNotFoundException(uri)
        }
    }

    override fun retainAccess(uri: String): String {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") return uri
        try {
            resolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        } catch (e: SecurityException) {
            // fall through to copy
        } catch (e: IllegalArgumentException) {
            // fall through to copy
        }
        // Files inside a device folder the user picked are covered by that folder's permission.
        if (coveredByPersistedTree(parsed)) return uri
        val safeName = (describe(uri)?.name ?: "file").replace('/', '_')
        val target = File(stagingDir, "${UUID.randomUUID()}_$safeName")
        val input = resolver.openInputStream(parsed) ?: throw IOException("Cannot open $uri")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return Uri.fromFile(target).toString()
    }

    override fun release(uri: String) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            val file = File(parsed.path ?: return)
            if (file.parentFile == stagingDir) file.delete()
            return
        }
        try {
            resolver.releasePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // permission was not persisted; nothing to release
        }
    }

    override fun retainTree(uri: String) {
        try {
            resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // not persistable; files will be copied to the staging directory instead
        }
    }

    override fun listTree(uri: String): TreeListing? {
        val tree = Uri.parse(uri)
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val rootName = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(tree, rootId)) ?: "folder"
        val files = mutableListOf<String>()
        val pending = mutableListOf(rootId)
        var truncated = false
        while (pending.isNotEmpty() && !truncated) {
            val parent = pending.removeAt(pending.lastIndex)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            try {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0) ?: continue
                        if (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(docId)
                        } else if (files.size >= MAX_TREE_FILES) {
                            truncated = true
                            break
                        } else {
                            files.add(DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString())
                        }
                    }
                }
            } catch (e: Exception) {
                // unreadable sub-folder: skip it
            }
        }
        return TreeListing(rootName, files, truncated)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun coveredByPersistedTree(uri: Uri): Boolean {
        val text = uri.toString()
        return resolver.persistedUriPermissions.any {
            it.isReadPermission && text.startsWith(it.uri.toString() + "/document/")
        }
    }

    private companion object {
        const val MAX_TREE_FILES = 500
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

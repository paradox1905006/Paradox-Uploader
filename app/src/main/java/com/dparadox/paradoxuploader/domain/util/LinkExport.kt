package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadStatus
import java.time.Instant

/** Turns a list of uploads into text (one link per line) or CSV. Only READY files have a link. */
object LinkExport {
    const val CSV_HEADER = "file_name,direct_url,item_url,folder,size_bytes,uploaded_at"

    fun exportable(items: List<UploadItem>): List<UploadItem> =
        items.filter { it.status == UploadStatus.READY && !it.directUrl.isNullOrBlank() }

    fun text(items: List<UploadItem>): String =
        exportable(items).joinToString("\n") { it.directUrl.orEmpty() }

    fun csv(items: List<UploadItem>, folderNames: Map<Long, String> = emptyMap()): String {
        val rows = exportable(items).map { item ->
            listOf(
                item.fileName,
                item.directUrl.orEmpty(),
                item.itemUrl,
                item.folderId?.let { folderNames[it] }.orEmpty(),
                item.sizeBytes.toString(),
                Instant.ofEpochMilli(item.createdAt).toString()
            ).joinToString(",") { escape(it) }
        }
        return (listOf(CSV_HEADER) + rows).joinToString("\r\n") + "\r\n"
    }

    internal fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

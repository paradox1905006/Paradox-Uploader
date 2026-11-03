package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.repository.RemoteFile

object RemoteFiles {
    /** True for files someone uploaded; false for what archive.org generates (thumbnails, torrents, *_meta.xml...). */
    fun isOriginal(file: RemoteFile): Boolean {
        if (file.name.isBlank() || file.name.contains('/')) return false
        file.source?.let { return it == "original" }
        val generated = file.name.endsWith("_meta.xml") || file.name.endsWith("_files.xml") ||
            file.name.endsWith("_meta.sqlite") || file.name.endsWith("_archive.torrent") ||
            file.name.startsWith("__ia_thumb")
        return !generated && file.format != "Metadata"
    }
}

package com.dparadox.paradoxuploader.domain.util

import okhttp3.HttpUrl

/** Builds every archive.org URL the app uses. Path segments are always percent-encoded. */
object ArchiveUrls {
    const val S3_HOST = "s3.us.archive.org"
    const val HOST = "archive.org"
    const val KEYS_PAGE = "https://archive.org/account/s3.php"
    const val REMOVE_ITEMS_HELP = "https://help.archive.org/help/removing-your-item-pages-from-archive-org/"
    const val TERMS_PAGE = "https://archive.org/about/terms.php"

    /** Removes characters that cannot be part of a single path segment. */
    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace('/', '_')
            .replace('\\', '_')
            .filter { it.code >= 0x20 }
            .trim()
        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "file" else cleaned
    }

    /** Returns [name], or "name_2.ext", "name_3.ext"... when [taken] already contains it. */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while ("${base}_$n$ext" in taken) n++
        return "${base}_$n$ext"
    }

    fun uploadUrl(identifier: String, fileName: String): HttpUrl =
        HttpUrl.Builder().scheme("https").host(S3_HOST)
            .addPathSegment(identifier)
            .addPathSegment(fileName)
            .build()

    fun downloadUrl(identifier: String, fileName: String): String =
        HttpUrl.Builder().scheme("https").host(HOST)
            .addPathSegment("download")
            .addPathSegment(identifier)
            .addPathSegment(fileName)
            .build()
            .toString()

    fun detailsUrl(identifier: String): String =
        HttpUrl.Builder().scheme("https").host(HOST)
            .addPathSegment("details")
            .addPathSegment(identifier)
            .build()
            .toString()

    fun metadataUrl(identifier: String): HttpUrl =
        HttpUrl.Builder().scheme("https").host(HOST)
            .addPathSegment("metadata")
            .addPathSegment(identifier)
            .build()
}

package com.dparadox.paradoxuploader.domain.telegram

object FileKinds {
    private val APK_EXT = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "lz4", "cab", "iso")

    fun classify(name: String, mime: String, base: FileKind): FileKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        val m = mime.lowercase()
        return when {
            base != FileKind.DOCUMENT && base != FileKind.OTHER -> base
            ext in APK_EXT || m == "application/vnd.android.package-archive" -> FileKind.APK
            ext in ARCHIVE_EXT || m.contains("zip") || m.contains("rar") || m.contains("x-7z") ||
                m.contains("x-tar") || m.contains("gzip") -> FileKind.ARCHIVE
            m.startsWith("video/") -> FileKind.VIDEO
            m.startsWith("audio/") -> FileKind.AUDIO
            m.startsWith("image/") -> FileKind.IMAGE
            else -> base
        }
    }

    fun extensionFor(mime: String, kind: FileKind): String = when (mime.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "audio/mpeg" -> "mp3"
        "audio/ogg" -> "ogg"
        "audio/mp4", "audio/aac" -> "m4a"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        "application/vnd.android.package-archive" -> "apk"
        else -> when (kind) {
            FileKind.IMAGE -> "jpg"
            FileKind.VIDEO, FileKind.ANIMATION -> "mp4"
            FileKind.VOICE -> "ogg"
            FileKind.AUDIO -> "mp3"
            else -> "bin"
        }
    }

    fun sanitizeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "telegram_file" }.take(180)
}

package com.dparadox.paradoxuploader.domain.model

import androidx.annotation.StringRes
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls

/** Lifecycle of an upload: UPLOADING -> PROCESSING -> READY / DELAYED / FAILED. */
enum class UploadStatus { UPLOADING, PROCESSING, READY, DELAYED, FAILED }

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER;

    companion object {
        private val DOC_EXT = setOf("pdf", "doc", "docx", "txt", "md", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "epub")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

        fun from(mimeType: String, fileName: String): FileType {
            val mime = mimeType.lowercase()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                mime == "application/pdf" || mime.startsWith("text/") || mime.contains("word") ||
                    mime.contains("officedocument") || mime.contains("opendocument") || ext in DOC_EXT -> DOCUMENT
                mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") ||
                    mime.contains("rar") || mime.contains("7z") || ext in ARCHIVE_EXT -> ARCHIVE
                else -> OTHER
            }
        }
    }
}

data class UploadItem(
    val id: Long,
    val identifier: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val createdAt: Long,
    val status: UploadStatus,
    val directUrl: String?,
    val lastCheckedAt: Long?,
    val errorMessage: String?,
    val folderId: Long? = null,
    val title: String? = null,
    val licenseUrl: String? = null
) {
    val itemUrl: String get() = ArchiveUrls.detailsUrl(identifier)
    val fileType: FileType get() = FileType.from(mimeType, fileName)
}

enum class IdentifierMode { NEW_PER_FILE, FIXED }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val identifierMode: IdentifierMode = IdentifierMode.NEW_PER_FILE,
    val fixedIdentifier: String = "",
    val defaultCollection: String = "opensource",
    val defaultMediatype: String = "data",
    val wifiOnly: Boolean = false,
    val concurrentUploads: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val appLock: Boolean = false,
    val onboardingDone: Boolean = false,
    val warningAccepted: Boolean = false,
    val syncUri: String = "",
    val lastSyncAt: Long = 0L,
    val autoRemoveEmptyFolders: Boolean = true,
    /** Pause all: uploads stay queued but nothing runs until resumed. */
    val uploadsPaused: Boolean = false,
    /** Only upload while the phone is charging. */
    val chargingOnly: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    /** Version the user chose "Later" for; not shown again until a newer one appears. */
    val dismissedUpdateVersion: String = ""
)

/** Plain class on purpose: no generated toString, so keys can never end up in logs. */
class Credentials(val accessKey: String, val secretKey: String) {
    override fun toString(): String = "Credentials(redacted)"
}

data class UploadMeta(
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val folder: Folder? = null
)

/**
 * A folder is one archive.org item: every file uploaded into it shares the same identifier,
 * so they all live on one page with one link.
 */
data class Folder(
    val id: Long,
    val name: String,
    val identifier: String,
    val createdAt: Long,
    val fileCount: Int = 0,
    /** Default metadata: used for every file uploaded into this folder. */
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val licenseUrl: String? = null
) {
    val itemUrl: String get() = ArchiveUrls.detailsUrl(identifier)
    val hasDefaults: Boolean
        get() = !title.isNullOrBlank() || !description.isNullOrBlank() || !tags.isNullOrBlank() || !licenseUrl.isNullOrBlank()
}

/** The metadata a folder applies to all of its files. Blank values mean "not set". */
data class FolderMeta(
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val licenseUrl: String? = null
)

/** Counts for the "Upload stats" card. Only files that reached archive.org are counted. */
data class UploadStats(
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val monthFiles: Int = 0,
    val monthBytes: Long = 0L,
    val todayFiles: Int = 0,
    val todayBytes: Long = 0L,
    val failedFiles: Int = 0
)

/** A newer app version found by the update check. */
data class UpdateInfo(val versionName: String, val url: String, val notes: String = "")

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/** Files found inside a folder the user picked on the device. */
data class TreeListing(val name: String, val files: List<String>, val truncated: Boolean = false)

/** Result of looking for archive.org items that have no files left. */
data class CleanupScan(val empty: List<String>, val failed: Int)

sealed interface HistoryResult {
    data class Success(val added: Int, val total: Int = 0) : HistoryResult
    data object InvalidFile : HistoryResult
    data object ReadFailed : HistoryResult
    data object WriteFailed : HistoryResult
    data class Remote(val kind: ErrorKind) : HistoryResult
}

data class UploadProgress(
    val bytesSent: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0) (bytesSent.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class SourceInfo(val name: String, val sizeBytes: Long, val mimeType: String)

enum class ErrorKind { AUTH, IDENTIFIER, SLOW_DOWN, NOT_FOUND, NETWORK, SERVER, UNKNOWN }

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(
        val kind: ErrorKind,
        val httpCode: Int? = null,
        val message: String = ""
    ) : ApiResult<Nothing>
}

sealed interface KeyTestResult {
    data class Success(val cleanedUp: Boolean) : KeyTestResult
    data class Failure(val kind: ErrorKind, val message: String) : KeyTestResult
}

sealed interface UploadOutcome {
    data object Done : UploadOutcome
    data object RetryLater : UploadOutcome
    data class Failed(val code: String) : UploadOutcome
}

sealed interface PollOutcome {
    data class Ready(val item: UploadItem) : PollOutcome
    data class Pending(val nextDelayMs: Long) : PollOutcome
    data object Delayed : PollOutcome
    data object Gone : PollOutcome
}

enum class EnqueueError { NO_KEYS, INVALID_IDENTIFIER }

data class EnqueueResult(val queued: Int, val skipped: Int, val error: EnqueueError?)

/** Codes stored in [UploadItem.errorMessage]; the UI maps them to localized strings. */
object ErrorCodes {
    const val AUTH = "AUTH"
    const val IDENTIFIER = "IDENTIFIER"
    const val NO_KEYS = "NO_KEYS"
    const val SOURCE_MISSING = "SOURCE_MISSING"
    const val NETWORK = "NETWORK"
    const val SERVER = "SERVER"
    const val UNKNOWN = "UNKNOWN"
}

/** A message for the UI layer (snackbar), expressed with string resources. */
data class UiMessage(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionRes: Int? = null,
    val onAction: (() -> Unit)? = null
)

package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.RemoteFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads byte ranges of a Telegram file through the user's own MTProto session.
 * TDLib downloads the requested range from Telegram's data center into its local cache;
 * we then read exactly that range from the cached file.
 */
@Singleton
class TdFileReader @Inject constructor(private val td: TdSession) : RemoteFileReader {

    // A new downloadFile with a different offset cancels the previous one for the same file,
    // so reads of one file are serialised.
    private val locks = ConcurrentHashMap<Int, Mutex>()

    override suspend fun read(fileId: Int, position: Long, maxBytes: Int): ByteArray =
        locks.getOrPut(fileId) { Mutex() }.withLock {
            repeat(MAX_ATTEMPTS) {
                val file = td.send(TdApi.DownloadFile(fileId, 32, position, maxBytes.toLong(), true))
                val total = file.size.takeIf { it > 0 } ?: file.expectedSize
                if (total in 1..position) return@withLock ByteArray(0)
                val available = td.send(TdApi.GetFileDownloadedPrefixSize(fileId, position)).size
                val n = minOf(available, maxBytes.toLong(), if (total > 0) total - position else Long.MAX_VALUE).toInt()
                if (n > 0) {
                    val path = file.local.path.ifEmpty { td.send(TdApi.GetFile(fileId)).local.path }
                    return@withLock withContext(Dispatchers.IO) {
                        RandomAccessFile(path, "r").use { raf ->
                            raf.seek(position)
                            ByteArray(n).also { raf.readFully(it) }
                        }
                    }
                }
            }
            throw IOException("Telegram returned no data at offset $position")
        }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

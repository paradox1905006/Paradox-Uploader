package com.dparadox.paradoxuploader.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * Streams the file in 64 KiB chunks and reports the number of bytes written so far.
 * The whole file is never held in memory. [opener] is called again if OkHttp retries the call.
 */
class ProgressRequestBody(
    private val type: MediaType?,
    private val length: Long,
    private val opener: () -> InputStream,
    private val onBytes: (Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = type

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        opener().source().use { source ->
            val buffer = Buffer()
            var total = 0L
            while (true) {
                val read = source.read(buffer, SEGMENT_SIZE)
                if (read == -1L) break
                sink.write(buffer, read)
                total += read
                onBytes(total)
            }
        }
    }

    private companion object {
        const val SEGMENT_SIZE = 64L * 1024L
    }
}

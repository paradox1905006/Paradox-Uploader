package com.dparadox.paradoxuploader.data.telegram

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dparadox.paradoxuploader.domain.telegram.DownloadProgress
import com.dparadox.paradoxuploader.domain.telegram.FileDownloadRepository
import com.dparadox.paradoxuploader.domain.telegram.RemoteFileReader
import com.dparadox.paradoxuploader.domain.telegram.ResolvedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Direct MTProto download (no HTTP involved): Telegram -> TDLib -> Downloads folder. */
@Singleton
class FileDownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: TdFileReader
) : FileDownloadRepository {

    private class Sink(val stream: OutputStream, val finish: () -> String, val abort: () -> Unit)

    override fun download(file: ResolvedFile): Flow<DownloadProgress> = flow {
        val sink = openSink(file)
        var finished = false
        try {
            var pos = 0L
            emit(DownloadProgress.Running(0, file.sizeBytes))
            while (pos < file.sizeBytes) {
                val chunk = reader.read(file.tdFileId, pos, minOf(CHUNK.toLong(), file.sizeBytes - pos).toInt())
                if (chunk.isEmpty()) throw IOException("Unexpected end of file")
                sink.stream.write(chunk)
                pos += chunk.size
                emit(DownloadProgress.Running(pos, file.sizeBytes))
            }
            sink.stream.flush()
            sink.stream.close()
            val uri = sink.finish()
            finished = true
            emit(DownloadProgress.Done(uri, file.fileName))
        } finally {
            if (!finished) {
                runCatching { sink.stream.close() }
                sink.abort()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun openSink(file: ResolvedFile): Sink {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.fileName)
                put(MediaStore.Downloads.MIME_TYPE, file.mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Telegram")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create download")
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open download")
            return Sink(
                stream,
                finish = {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    uri.toString()
                },
                abort = { runCatching { resolver.delete(uri, null, null) } }
            )
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Telegram").apply { mkdirs() }
        var target = File(dir, file.fileName)
        var n = 1
        while (target.exists()) {
            val base = file.fileName.substringBeforeLast('.', file.fileName)
            val ext = file.fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            target = File(dir, "$base ($n)$ext")
            n++
        }
        val stream = target.outputStream().buffered()
        return Sink(stream, finish = { Uri.fromFile(target).toString() }, abort = { target.delete() })
    }

    private companion object {
        const val CHUNK = 1 shl 20
    }
}

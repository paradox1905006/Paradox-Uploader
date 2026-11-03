package com.dparadox.paradoxuploader.data.telegram

import com.dparadox.paradoxuploader.domain.telegram.RemoteFileReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * Minimal HTTP/1.1 server bound to 127.0.0.1 only. It lets a browser, download manager or media
 * player on THIS device fetch a Telegram file; every byte is pulled from Telegram with the user's
 * own session. Nothing leaves the device and no outside infrastructure is involved.
 *
 * Supports GET/HEAD and single `Range` requests (resume + seeking).
 */
class LocalStreamServer(
    private val registry: LinkRegistry,
    private val reader: RemoteFileReader,
    private val scope: CoroutineScope
) {
    private var server: ServerSocket? = null
    @Volatile var port: Int = 0
        private set

    @Synchronized
    fun ensureStarted(): Int {
        server?.let { if (!it.isClosed) return port }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        port = s.localPort
        scope.launch(Dispatchers.IO) { acceptLoop(s) }
        return port
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private suspend fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val socket = try { s.accept() } catch (e: IOException) { break }
            scope.launch(Dispatchers.IO) { runCatching { handle(socket) } }
        }
    }

    private suspend fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 30_000
            val input = BufferedInputStream(sock.getInputStream())
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
            val request = readRequest(input) ?: return

            if (request.method != "GET" && request.method != "HEAD") {
                return respond(out, 405, "Method Not Allowed", mapOf("Allow" to "GET, HEAD"))
            }
            val host = request.headers["host"]?.substringBefore(':')?.lowercase()
            if (host != "127.0.0.1" && host != "localhost") {
                return respond(out, 403, "Forbidden")
            }
            val segments = request.path.substringBefore('?').trim('/').split('/')
            val entry = (if (segments.size >= 2 && segments[0] == "dl") registry.get(segments[1]) else null)
                ?: return respond(out, 404, "Not Found")

            val total = entry.size
            val range = parseRange(request.headers["range"], total)
            if (range == RangeResult.Unsatisfiable) {
                return respond(out, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$total"))
            }
            val (start, end) = (range as? RangeResult.Partial)?.let { it.start to it.end } ?: (0L to total - 1)
            val length = end - start + 1
            val disposition = if ("inline=1" in request.path) "inline" else "attachment"
            val encoded = URLEncoder.encode(entry.fileName, "UTF-8").replace("+", "%20")
            val asciiName = entry.fileName.filter { it.code in 32..126 && it != '"' && it != '\\' }.ifBlank { "file" }

            val headers = linkedMapOf(
                "Content-Type" to entry.mimeType,
                "Content-Length" to length.toString(),
                "Accept-Ranges" to "bytes",
                "Content-Disposition" to "$disposition; filename=\"$asciiName\"; filename*=UTF-8''$encoded",
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff"
            )
            if (range is RangeResult.Partial) headers["Content-Range"] = "bytes $start-$end/$total"
            writeHead(out, if (range is RangeResult.Partial) 206 else 200, if (range is RangeResult.Partial) "Partial Content" else "OK", headers)
            if (request.method == "GET") {
                var pos = start
                var remaining = length
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    val chunk = reader.read(entry.fileId, pos, minOf(CHUNK.toLong(), remaining).toInt())
                    if (chunk.isEmpty()) break
                    out.write(chunk)
                    pos += chunk.size
                    remaining -= chunk.size
                }
            }
            out.flush()
        }
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>)

    private sealed interface RangeResult {
        data object Full : RangeResult
        data object Unsatisfiable : RangeResult
        data class Partial(val start: Long, val end: Long) : RangeResult
    }

    private fun parseRange(header: String?, total: Long): RangeResult {
        if (header.isNullOrBlank() || !header.startsWith("bytes=")) return RangeResult.Full
        val spec = header.removePrefix("bytes=").trim()
        if (spec.contains(',')) return RangeResult.Full
        val from = spec.substringBefore('-').trim()
        val to = spec.substringAfter('-', "").trim()
        return try {
            when {
                from.isEmpty() -> {
                    val suffix = to.toLong()
                    if (suffix <= 0) RangeResult.Unsatisfiable
                    else RangeResult.Partial(maxOf(0, total - suffix), total - 1)
                }
                else -> {
                    val s = from.toLong()
                    val e = if (to.isEmpty()) total - 1 else minOf(to.toLong(), total - 1)
                    if (s >= total || e < s) RangeResult.Unsatisfiable else RangeResult.Partial(s, e)
                }
            }
        } catch (e: NumberFormatException) {
            RangeResult.Full
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val first = readLine(input) ?: return null
        val parts = first.split(' ')
        if (parts.size < 3) return null
        val headers = HashMap<String, String>()
        repeat(MAX_HEADERS) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) return Request(parts[0].uppercase(), parts[1], headers)
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        return null
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < MAX_LINE) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, reason: String, extra: Map<String, String> = emptyMap()) {
        writeHead(out, code, reason, linkedMapOf("Content-Length" to "0", "Cache-Control" to "no-store") + extra)
        out.flush()
    }

    private fun writeHead(out: OutputStream, code: Int, reason: String, headers: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private companion object {
        const val CHUNK = 1 shl 20
        const val MAX_HEADERS = 64
        const val MAX_LINE = 8192
    }
}

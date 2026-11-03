package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.dparadox.paradoxuploader.domain.telegram.DirectLink
import com.dparadox.paradoxuploader.domain.telegram.DirectLinkRepository
import com.dparadox.paradoxuploader.domain.telegram.ResolvedFile
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.domain.telegram.TgResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.SecureRandom
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram does not hand out shareable HTTP URLs for a user session (Telegram Web streams
 * file parts over MTProto inside a service worker). The closest client-side equivalent is a
 * short-lived URL on 127.0.0.1 that streams the file straight from Telegram with the user's session.
 */
@Singleton
class LocalDirectLinkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    reader: TdFileReader
) : DirectLinkRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registry = LinkRegistry()
    private val server = LocalStreamServer(registry, reader, scope)
    private val random = SecureRandom()
    private var sweeper: Job? = null

    private val _active = MutableStateFlow(0)
    override val activeLinks: StateFlow<Int> = _active

    override suspend fun create(file: ResolvedFile, ttlMinutes: Int): TgResult<DirectLink> {
        if (file.sizeBytes <= 0) return TgResult.Failure(TgError.LinkGenerationFailed)
        val port = try {
            server.ensureStarted()
        } catch (e: IOException) {
            return TgResult.Failure(TgError.LinkGenerationFailed)
        }
        val tokenBytes = ByteArray(18).also { random.nextBytes(it) }
        val token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val now = System.currentTimeMillis()
        val expires = now + ttlMinutes.coerceIn(1, 240) * 60_000L
        registry.put(LinkEntry(token, file.tdFileId, file.fileName, file.mimeType, file.sizeBytes, expires))
        _active.value = registry.sweep()
        startSweeper()
        ContextCompat.startForegroundService(context, Intent(context, DirectLinkService::class.java))
        val url = "http://127.0.0.1:$port/dl/$token/${Uri.encode(file.fileName)}"
        return TgResult.Success(DirectLink(url, token, file.fileName, now, expires))
    }

    override fun revoke(token: String) {
        registry.remove(token)
        _active.value = registry.sweep()
        if (_active.value == 0) server.stop()
    }

    override fun revokeAll() {
        registry.clear()
        _active.value = 0
        server.stop()
    }

    private fun startSweeper() {
        if (sweeper?.isActive == true) return
        sweeper = scope.launch {
            while (isActive) {
                delay(10_000)
                val n = registry.sweep()
                _active.value = n
                if (n == 0) {
                    server.stop()
                    break
                }
            }
        }
    }
}

package com.dparadox.paradoxuploader.data.remote

import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.RemoteFile
import com.dparadox.paradoxuploader.domain.repository.UploadRequest
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.domain.util.MetaHeaders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class MetadataDto(val files: List<FileDto> = emptyList())

@Serializable
internal data class FileDto(
    val name: String = "",
    val size: JsonElement? = null,
    val format: String? = null,
    val mtime: JsonElement? = null,
    val source: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _ -> response.close() }
        }
    })
}

@Singleton
class OkHttpArchiveService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : ArchiveService {

    override suspend fun upload(
        credentials: Credentials,
        request: UploadRequest,
        body: RequestBody
    ): ApiResult<Unit> = guarded {
        val builder = Request.Builder()
            .url(ArchiveUrls.uploadUrl(request.identifier, request.fileName))
            .put(body)
            .header("Authorization", auth(credentials))
            .header("x-archive-auto-make-bucket", "1")
            .header("x-archive-meta01-collection", request.collection)
            .header("x-archive-meta-mediatype", request.mediatype)
            .header("x-archive-meta-title", MetaHeaders.encode(request.title ?: request.fileName))
            .header("x-archive-interactive-priority", "1")
        if (request.sizeBytes > 0) builder.header("x-archive-size-hint", request.sizeBytes.toString())
        request.description?.takeIf { it.isNotBlank() }?.let {
            builder.header("x-archive-meta-description", MetaHeaders.encode(it))
        }
        request.licenseUrl?.takeIf { it.isNotBlank() }?.let {
            builder.header("x-archive-meta-licenseurl", MetaHeaders.encode(it))
        }
        request.tags?.takeIf { it.isNotBlank() }?.let { raw ->
            val subjects = raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";")
            if (subjects.isNotEmpty()) builder.header("x-archive-meta-subject", MetaHeaders.encode(subjects))
        }
        client.newCall(builder.build()).await().use { response ->
            if (response.isSuccessful) ApiResult.Success(Unit) else response.toFailure()
        }
    }

    override suspend fun deleteFile(
        credentials: Credentials,
        identifier: String,
        fileName: String
    ): ApiResult<Unit> = guarded {
        val request = Request.Builder()
            .url(ArchiveUrls.uploadUrl(identifier, fileName))
            .delete()
            .header("Authorization", auth(credentials))
            .header("x-archive-cascade-delete", "1")
            .build()
        client.newCall(request).await().use { response ->
            if (response.isSuccessful) ApiResult.Success(Unit) else response.toFailure()
        }
    }

    override suspend fun fetchMetadata(identifier: String): ApiResult<List<RemoteFile>> = guarded {
        val request = Request.Builder().url(ArchiveUrls.metadataUrl(identifier)).get().build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use response.toFailure()
            val text = response.body?.string().orEmpty()
            try {
                val dto = json.decodeFromString<MetadataDto>(text.ifBlank { "{}" })
                ApiResult.Success(
                    dto.files.map {
                        RemoteFile(
                            name = it.name,
                            sizeBytes = (it.size as? JsonPrimitive)?.content?.toLongOrNull(),
                            format = it.format,
                            mtime = (it.mtime as? JsonPrimitive)?.content?.toLongOrNull(),
                            source = it.source
                        )
                    }
                )
            } catch (e: SerializationException) {
                ApiResult.Failure(ErrorKind.UNKNOWN, response.code, "Unreadable metadata response")
            }
        }
    }

    override suspend fun updateItemMetadata(
        credentials: Credentials,
        identifier: String,
        meta: FolderMeta
    ): ApiResult<Unit> = guarded {
        val ops = mutableListOf<JsonObject>()
        fun addOp(field: String, value: JsonElement) {
            ops += buildJsonObject {
                put("op", "add")
                put("path", "/$field")
                put("value", value)
            }
        }
        meta.title?.takeIf { it.isNotBlank() }?.let { addOp("title", JsonPrimitive(it.trim())) }
        meta.description?.takeIf { it.isNotBlank() }?.let { addOp("description", JsonPrimitive(it.trim())) }
        meta.licenseUrl?.takeIf { it.isNotBlank() }?.let { addOp("licenseurl", JsonPrimitive(it.trim())) }
        meta.tags?.takeIf { it.isNotBlank() }?.let { raw ->
            val subjects = raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (subjects.isNotEmpty()) addOp("subject", JsonArray(subjects.map { JsonPrimitive(it) }))
        }
        if (ops.isEmpty()) return@guarded ApiResult.Success(Unit)
        val form = FormBody.Builder()
            .add("-target", "metadata")
            .add("-patch", JsonArray(ops).toString())
            .add("access", credentials.accessKey)
            .add("secret", credentials.secretKey)
            .build()
        val request = Request.Builder()
            .url(ArchiveUrls.metadataUrl(identifier))
            .post(form)
            .header("Authorization", auth(credentials))
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) return@use response.toFailure()
            val text = response.body?.string().orEmpty().replace(" ", "")
            if (text.contains("\"success\":false")) {
                ApiResult.Failure(ErrorKind.UNKNOWN, response.code, "Metadata update was rejected")
            } else {
                ApiResult.Success(Unit)
            }
        }
    }

    override suspend fun isDownloadable(identifier: String, fileName: String): ApiResult<Boolean> = guarded {
        val request = Request.Builder()
            .url(ArchiveUrls.downloadUrl(identifier, fileName))
            .head()
            .build()
        client.newCall(request).await().use { response ->
            ApiResult.Success(response.code == 200)
        }
    }

    private fun auth(credentials: Credentials) = "LOW ${credentials.accessKey}:${credentials.secretKey}"

    private suspend fun <T> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.Failure(ErrorKind.NETWORK, null, e.message.orEmpty())
    } catch (e: IllegalArgumentException) {
        ApiResult.Failure(ErrorKind.UNKNOWN, null, "Invalid request")
    }

    private fun Response.toFailure(): ApiResult.Failure {
        val text = runCatching { body?.string() }.getOrNull().orEmpty()
        val message = Regex("<Message>(.*?)</Message>", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1).orEmpty()
        val kind = when (code) {
            401, 403 -> ErrorKind.AUTH
            400, 409 -> ErrorKind.IDENTIFIER
            404 -> ErrorKind.NOT_FOUND
            429, 503 -> ErrorKind.SLOW_DOWN
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.UNKNOWN
        }
        return ApiResult.Failure(kind, code, message.take(200))
    }
}

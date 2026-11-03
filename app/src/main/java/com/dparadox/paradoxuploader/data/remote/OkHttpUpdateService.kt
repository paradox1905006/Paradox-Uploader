package com.dparadox.paradoxuploader.data.remote

import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.UpdateInfo
import com.dparadox.paradoxuploader.domain.repository.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the latest version from [BuildConfig.UPDATE_CHECK_URL]. Two JSON shapes are understood:
 * a GitHub "latest release" response (`tag_name`, `html_url`, `body`) or a small file you host
 * yourself: `{"versionName": "1.3.0", "url": "https://...", "notes": "..."}`.
 */
@Singleton
class OkHttpUpdateService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : UpdateService {

    override suspend fun latest(): ApiResult<UpdateInfo> = withContext(Dispatchers.IO) {
        val url = BuildConfig.UPDATE_CHECK_URL.toHttpUrlOrNull()
            ?: return@withContext ApiResult.Failure(ErrorKind.UNKNOWN, null, "No update URL configured")
        try {
            val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use ApiResult.Failure(
                        if (response.code == 404) ErrorKind.NOT_FOUND else ErrorKind.SERVER,
                        response.code,
                        ""
                    )
                }
                parse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            ApiResult.Failure(ErrorKind.NETWORK, null, e.message.orEmpty())
        }
    }

    private fun parse(text: String): ApiResult<UpdateInfo> {
        val obj = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            return ApiResult.Failure(ErrorKind.UNKNOWN, null, "Unreadable update response")
        } catch (e: IllegalArgumentException) {
            return ApiResult.Failure(ErrorKind.UNKNOWN, null, "Unreadable update response")
        }
        val version = obj.text("versionName", "tag_name", "version")
            ?: return ApiResult.Failure(ErrorKind.UNKNOWN, null, "No version in update response")
        val link = obj.text("url", "html_url") ?: return ApiResult.Failure(ErrorKind.UNKNOWN, null, "No link")
        val notes = obj.text("notes", "body").orEmpty().take(600)
        return ApiResult.Success(UpdateInfo(version, link, notes))
    }

    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
}

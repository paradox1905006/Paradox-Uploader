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
import kotlinx.serialization.json.JsonArray
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
                    // GitHub's anonymous API allows only 60 requests/hour per IP. Phones behind a
                    // carrier's shared (CGNAT) IP, or behind a VPN, commonly hit this as 403/429
                    // even though the phone itself has a perfectly good connection.
                    val reason = when (response.code) {
                        403, 429 -> "HTTP ${response.code}: rate limited (shared IP?)"
                        else -> "HTTP ${response.code}"
                    }
                    return@use ApiResult.Failure(
                        if (response.code == 404) ErrorKind.NOT_FOUND else ErrorKind.SERVER,
                        response.code,
                        reason
                    )
                }
                parse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            ApiResult.Failure(ErrorKind.NETWORK, null, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
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
        // A GitHub "latest release" response has its own "url" field pointing at the API
        // endpoint (not a page a person should open), so the two shapes must be told apart
        // instead of just trying "url" then "html_url" in order.
        val isGithubRelease = obj.containsKey("tag_name")
        val version = (if (isGithubRelease) obj.text("tag_name") else obj.text("versionName", "version"))
            ?.removePrefix("V")?.removePrefix("v")
            ?: return ApiResult.Failure(ErrorKind.UNKNOWN, null, "No version in update response")
        val link = (if (isGithubRelease) obj.text("html_url") else obj.text("url"))
            ?: return ApiResult.Failure(ErrorKind.UNKNOWN, null, "No link")
        val notes = (if (isGithubRelease) obj.text("body") else obj.text("notes")).orEmpty().take(600)
        val apkUrl = if (isGithubRelease) apkAssetUrl(obj) else obj.text("apkUrl", "apk_url")
        return ApiResult.Success(UpdateInfo(version, link, notes, apkUrl))
    }

    /** Finds the first .apk release asset's direct download link in a GitHub release response. */
    private fun apkAssetUrl(obj: JsonObject): String? {
        val assets = obj["assets"] as? JsonArray ?: return null
        return assets.firstNotNullOfOrNull { element ->
            val asset = element as? JsonObject ?: return@firstNotNullOfOrNull null
            val name = (asset["name"] as? JsonPrimitive)?.contentOrNull ?: return@firstNotNullOfOrNull null
            if (!name.endsWith(".apk", ignoreCase = true)) return@firstNotNullOfOrNull null
            (asset["browser_download_url"] as? JsonPrimitive)?.contentOrNull
        }
    }

    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
}

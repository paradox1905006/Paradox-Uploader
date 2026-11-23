package com.dparadox.paradoxuploader.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Downloads a release .apk in the background and hands it to the system installer. */
object UpdateDownloader {

    sealed interface Result {
        data object Installing : Result
        data object NeedsInstallPermission : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun downloadAndInstall(context: Context, client: OkHttpClient, apkUrl: String): Result =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "update.apk")
            val partial = File(dir, "update.apk.part")
            try {
                val request = Request.Builder().url(apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.Failed("HTTP ${response.code}")
                    }
                    val body = response.body ?: return@withContext Result.Failed("Empty response")
                    // Write to a temp file first: an interrupted download must never be handed to the installer.
                    body.byteStream().use { input ->
                        partial.outputStream().use { output -> input.copyTo(output) }
                    }
                    val expected = body.contentLength()
                    if (expected > 0 && partial.length() != expected) {
                        partial.delete()
                        return@withContext Result.Failed("Download incomplete")
                    }
                    file.delete()
                    if (!partial.renameTo(file)) {
                        partial.delete()
                        return@withContext Result.Failed("Could not save the update")
                    }
                    withContext(Dispatchers.Main) { launchInstall(context, file) }
                }
            } catch (e: CancellationException) {
                partial.delete()
                throw e
            } catch (e: IOException) {
                partial.delete()
                Result.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            } catch (e: Exception) {
                // Bad URL in update.json, no installer on the device, etc.: report it instead of crashing.
                partial.delete()
                Result.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }

    private fun launchInstall(context: Context, file: File): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return Result.NeedsInstallPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installIntent)
        return Result.Installing
    }
}

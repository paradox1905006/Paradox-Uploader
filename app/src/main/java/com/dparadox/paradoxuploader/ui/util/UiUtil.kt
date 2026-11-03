package com.dparadox.paradoxuploader.ui.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.core.content.FileProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.ErrorCodes
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiActions {
    fun copy(context: Context, text: String, @StringRes messageRes: Int = R.string.link_copied) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("link", text))
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    /** Shares [content] as a real file (for example a .csv) through the system share sheet. */
    fun shareFile(context: Context, content: String, fileName: String, mimeType: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, text: String) {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            null
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun open(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    fun download(context: Context, item: UploadItem) {
        val url = item.directUrl ?: return
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(item.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.fileName)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
    }
}

object ErrorTexts {
    @StringRes
    fun resId(code: String?): Int? = when (code) {
        ErrorCodes.AUTH -> R.string.error_auth
        ErrorCodes.IDENTIFIER -> R.string.error_identifier
        ErrorCodes.NO_KEYS -> R.string.error_no_keys
        ErrorCodes.SOURCE_MISSING -> R.string.error_source_missing
        ErrorCodes.NETWORK -> R.string.error_network
        ErrorCodes.SERVER -> R.string.error_server
        ErrorCodes.UNKNOWN -> R.string.error_unknown
        else -> null
    }

    fun text(context: Context, code: String?): String =
        resId(code)?.let { context.getString(it) } ?: code.orEmpty()
}

@Composable
fun errorText(code: String?): String = ErrorTexts.resId(code)?.let { stringResource(it) } ?: code.orEmpty()

object QrCode {
    fun bitmap(text: String, size: Int = 768): Bitmap {
        val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { index ->
            if (matrix.get(index % size, index / size)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

object AppLockSupport {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun allowedAuthenticators(): Int = AUTHENTICATORS

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

/** Date and time in the device locale, e.g. "21 Oct 2026, 3:45 PM". */
fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMyyyyjmm")
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

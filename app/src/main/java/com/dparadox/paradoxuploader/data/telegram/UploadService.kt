package com.dparadox.paradoxuploader.data.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.dparadox.paradoxuploader.domain.telegram.UploadController
import com.dparadox.paradoxuploader.domain.telegram.UploadJob
import com.dparadox.paradoxuploader.domain.telegram.UploadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps uploads running when the user leaves the app. Shows live progress in the notification
 * and stops itself once nothing is waiting or uploading.
 */
@AndroidEntryPoint
class UploadService : Service() {

    @Inject lateinit var controller: UploadController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // Must be called promptly after startForegroundService.
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            build("Preparing uploads…", null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        watch = scope.launch {
            controller.jobs.collect { list ->
                val active = list.filter { it.status == UploadStatus.WAITING || it.status == UploadStatus.UPLOADING }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notifyProgress(active)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        watch?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyProgress(active: List<UploadJob>) {
        val uploading = active.firstOrNull { it.status == UploadStatus.UPLOADING }
        val text = if (uploading != null) {
            val pct = if (uploading.sizeBytes > 0) (uploading.uploadedBytes * 100 / uploading.sizeBytes).toInt().coerceIn(0, 100) else 0
            "${uploading.fileName} · $pct% · ${active.size} in queue"
        } else {
            "${active.size} waiting"
        }
        val pct = uploading?.let { if (it.sizeBytes > 0) (it.uploadedBytes * 100 / it.sizeBytes).toInt().coerceIn(0, 100) else 0 }
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, build(text, pct))
        } catch (_: SecurityException) {
            // Notifications disabled: the service keeps running in the foreground regardless.
        }
    }

    private fun build(text: String, percent: Int?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading to Telegram")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telegram uploads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val NOTIF_ID = 4721
        private const val CHANNEL_ID = "telegram_uploads"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UploadService::class.java))
        }
    }
}

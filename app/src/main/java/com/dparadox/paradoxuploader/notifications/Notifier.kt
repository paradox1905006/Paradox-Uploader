package com.dparadox.paradoxuploader.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.dparadox.paradoxuploader.MainActivity
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.UiMessage
import com.dparadox.paradoxuploader.domain.model.UploadItem
import com.dparadox.paradoxuploader.domain.model.UploadProgress
import com.dparadox.paradoxuploader.domain.util.AppEventBus
import com.dparadox.paradoxuploader.domain.util.Formatters
import com.dparadox.paradoxuploader.ui.util.ErrorTexts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bus: AppEventBus
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CH_UPLOADS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_uploads)).build(),
                NotificationChannelCompat.Builder(CH_READY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_ready)).build(),
                NotificationChannelCompat.Builder(CH_ERRORS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_errors)).build()
            )
        )
    }

    // region progress (foreground service)

    fun foregroundInfo(item: UploadItem, progress: UploadProgress?): ForegroundInfo =
        ForegroundInfo(
            progressId(item.id),
            progressNotification(item, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    fun progressNotification(item: UploadItem, progress: UploadProgress?): Notification {
        val percent = progress?.let { (it.fraction * 100).toInt() } ?: 0
        val text = if (progress == null) {
            context.getString(R.string.notif_starting)
        } else {
            context.getString(
                R.string.notif_progress,
                percent,
                Formatters.speed(progress.speedBps),
                Formatters.eta(progress.etaSeconds)
            )
        }
        val cancel = broadcast(
            code(item.id, 1),
            Intent(context, ActionReceiver::class.java)
                .setAction(AppIntents.ACTION_CANCEL)
                .putExtra(AppIntents.EXTRA_FILE_ID, item.id)
        )
        return NotificationCompat.Builder(context, CH_UPLOADS)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(item.fileName)
            .setContentText(text)
            .setProgress(100, percent, progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activity(code(item.id, 0), openFilesIntent(null)))
            .addAction(0, context.getString(R.string.cancel), cancel)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateProgress(item: UploadItem, progress: UploadProgress) {
        if (!manager.areNotificationsEnabled()) return
        manager.notify(progressId(item.id), progressNotification(item, progress))
    }

    // endregion

    // region batch (many files / a folder at once)

    /** One notification for the whole batch: "Uploading files: 3 of 12 done" with an overall bar. */
    @SuppressLint("MissingPermission")
    fun showBatchProgress(done: Int, total: Int, percent: Int, speedBps: Long, etaSeconds: Long) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CH_UPLOADS)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_batch_title, done, total))
            .setContentText(
                context.getString(R.string.notif_progress, percent, Formatters.speed(speedBps), Formatters.eta(etaSeconds))
            )
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activity(BATCH_CODE, openFilesIntent(null)))
            .build()
        manager.notify(BATCH_ID, notification)
    }

    fun clearBatchProgress() {
        manager.cancel(BATCH_ID)
    }

    @SuppressLint("MissingPermission")
    fun showBatchDone(uploaded: Int, failed: Int) {
        if (!manager.areNotificationsEnabled()) return
        val text = if (failed == 0) {
            context.getString(R.string.notif_batch_done_text, uploaded)
        } else {
            context.getString(R.string.notif_batch_done_failed, uploaded, failed)
        }
        val notification = NotificationCompat.Builder(context, CH_READY)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_batch_done_title))
            .setContentText(text)
            .setContentIntent(activity(BATCH_CODE, openFilesIntent(null)))
            .setAutoCancel(true)
            .build()
        manager.notify(BATCH_DONE_ID, notification)
    }

    // endregion

    // region link ready

    @SuppressLint("MissingPermission")
    fun showLinkReady(item: UploadItem) {
        val url = item.directUrl ?: return
        if (!manager.areNotificationsEnabled()) {
            bus.post(UiMessage(R.string.snackbar_link_ready, listOf(item.fileName)))
            return
        }
        val openFiles = activity(code(item.id, 0), openFilesIntent(item.id))
        val copy = broadcast(
            code(item.id, 1),
            Intent(context, ActionReceiver::class.java)
                .setAction(AppIntents.ACTION_COPY)
                .putExtra(AppIntents.EXTRA_URL, url)
        )
        val shareIntent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
            null
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val share = activity(code(item.id, 2), shareIntent)

        val notification = NotificationCompat.Builder(context, CH_READY)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_link_ready))
            .setContentText(item.fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(url))
            .setContentIntent(openFiles)
            .setAutoCancel(true)
            .setGroup(GROUP_READY)
            .addAction(0, context.getString(R.string.copy_link), copy)
            .addAction(0, context.getString(R.string.share), share)
            .addAction(0, context.getString(R.string.open_my_files), openFiles)
            .build()
        val summary = NotificationCompat.Builder(context, CH_READY)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_links_ready_summary))
            .setContentText(context.getString(R.string.notif_summary_hint))
            .setGroup(GROUP_READY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(activity(SUMMARY_CODE, openFilesIntent(null)))
            .build()
        manager.notify(readyId(item.id), notification)
        manager.notify(SUMMARY_ID, summary)
    }

    // endregion

    // region failures

    @SuppressLint("MissingPermission")
    fun showFailure(item: UploadItem, errorCode: String?) {
        val reason = ErrorTexts.text(context, errorCode)
        if (!manager.areNotificationsEnabled()) {
            bus.post(UiMessage(R.string.snackbar_upload_failed, listOf(item.fileName)))
            return
        }
        val retry = broadcast(
            code(item.id, 3),
            Intent(context, ActionReceiver::class.java)
                .setAction(AppIntents.ACTION_RETRY)
                .putExtra(AppIntents.EXTRA_FILE_ID, item.id)
        )
        val notification = NotificationCompat.Builder(context, CH_ERRORS)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_upload_failed))
            .setContentText(item.fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.fileName}\n$reason"))
            .setContentIntent(activity(code(item.id, 0), openFilesIntent(null)))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.retry), retry)
            .build()
        manager.notify(failureId(item.id), notification)
    }

    // endregion

    private fun openFilesIntent(fileId: Long?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(AppIntents.ACTION_OPEN_FILES)
            .apply { if (fileId != null) putExtra(AppIntents.EXTRA_FILE_ID, fileId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun activity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, FLAGS)

    private fun broadcast(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, FLAGS)

    private fun code(id: Long, slot: Int): Int = id.toInt() * 4 + slot

    private fun progressId(id: Long) = PROGRESS_BASE + id.toInt()
    private fun readyId(id: Long) = READY_BASE + id.toInt()
    private fun failureId(id: Long) = FAILURE_BASE + id.toInt()

    private companion object {
        const val CH_UPLOADS = "uploads"
        const val CH_READY = "link_ready"
        const val CH_ERRORS = "upload_errors"
        const val GROUP_READY = "link_ready_group"
        const val BATCH_ID = 1_000
        const val BATCH_DONE_ID = 1_001
        const val BATCH_CODE = -2
        const val SUMMARY_ID = 2_000
        const val SUMMARY_CODE = -1
        const val PROGRESS_BASE = 10_000
        const val READY_BASE = 20_000
        const val FAILURE_BASE = 30_000
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

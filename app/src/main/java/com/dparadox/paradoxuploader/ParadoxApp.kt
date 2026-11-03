package com.dparadox.paradoxuploader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.telegram.TelegramAuthRepository
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.repository.HistoryRepository
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.notifications.BatchProgressMonitor
import com.dparadox.paradoxuploader.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ParadoxApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var repository: UploadRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var folders: FolderRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var batchMonitor: BatchProgressMonitor
    @Inject lateinit var telegramAuth: TelegramAuthRepository
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        // Re-schedule anything the queue in Room still knows about (process death / reboot).
        appScope.launch { repository.resumePending() }
        batchMonitor.start()
        // Restore the Telegram session (encrypted TDLib database) in the background, if API credentials exist.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { telegramAuth.start() }
        // Remove folders that have no files (kept: folders created in the last 10 minutes).
        appScope.launch {
            if (settings.current().autoRemoveEmptyFolders) folders.removeEmpty(EMPTY_FOLDER_MIN_AGE_MS)
        }
        // Keep the shared history file up to date when the app starts (at most every 5 minutes).
        appScope.launch {
            val current = settings.current()
            if (current.syncUri.isNotBlank() && System.currentTimeMillis() - current.lastSyncAt > SYNC_INTERVAL_MS) {
                history.syncWithDocument(current.syncUri)
            }
        }
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 5 * 60 * 1000L
        const val EMPTY_FOLDER_MIN_AGE_MS = 10 * 60 * 1000L
    }
}

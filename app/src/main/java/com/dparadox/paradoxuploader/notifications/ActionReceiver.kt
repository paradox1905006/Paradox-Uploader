package com.dparadox.paradoxuploader.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dparadox.paradoxuploader.di.ApplicationScope
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.ui.util.UiActions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ActionReceiverEntryPoint {
    fun repository(): UploadRepository

    @ApplicationScope
    fun appScope(): CoroutineScope
}

/** Handles notification actions: copy link, cancel an upload, retry a failed upload. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppIntents.ACTION_COPY -> {
                intent.getStringExtra(AppIntents.EXTRA_URL)?.let { UiActions.copy(context, it) }
            }
            AppIntents.ACTION_CANCEL, AppIntents.ACTION_RETRY -> {
                val id = intent.getLongExtra(AppIntents.EXTRA_FILE_ID, -1L)
                if (id < 0) return
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ActionReceiverEntryPoint::class.java
                )
                val cancel = intent.action == AppIntents.ACTION_CANCEL
                val pending = goAsync()
                entryPoint.appScope().launch {
                    try {
                        if (cancel) entryPoint.repository().cancel(id) else entryPoint.repository().retry(id)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

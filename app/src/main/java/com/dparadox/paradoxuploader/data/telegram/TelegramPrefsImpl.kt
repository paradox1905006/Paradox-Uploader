package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dparadox.paradoxuploader.domain.telegram.TelegramPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tgStore by preferencesDataStore(name = "telegram_prefs")

@Singleton
class TelegramPrefsImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TelegramPrefs {
    private val ttlKey = intPreferencesKey("link_ttl_minutes")

    override val linkTtlMinutes: Flow<Int> = context.tgStore.data.map { it[ttlKey] ?: DEFAULT_TTL }

    override suspend fun setLinkTtlMinutes(minutes: Int) {
        context.tgStore.edit { it[ttlKey] = minutes.coerceIn(1, 240) }
    }

    companion object {
        const val DEFAULT_TTL = 60
        val OPTIONS = listOf(15, 30, 60, 120, 240)
    }
}

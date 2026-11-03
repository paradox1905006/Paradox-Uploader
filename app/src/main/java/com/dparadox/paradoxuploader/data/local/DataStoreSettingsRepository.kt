package com.dparadox.paradoxuploader.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.IdentifierMode
import com.dparadox.paradoxuploader.domain.model.ThemeMode
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val MODE = stringPreferencesKey("identifier_mode")
        val FIXED_ID = stringPreferencesKey("fixed_identifier")
        val COLLECTION = stringPreferencesKey("default_collection")
        val MEDIATYPE = stringPreferencesKey("default_mediatype")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CONCURRENT = intPreferencesKey("concurrent_uploads")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val LOCK = booleanPreferencesKey("app_lock")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val WARNING = booleanPreferencesKey("warning_accepted")
        val SYNC_URI = stringPreferencesKey("sync_uri")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val AUTO_EMPTY = booleanPreferencesKey("auto_remove_empty_folders")
        val PAUSED = booleanPreferencesKey("uploads_paused")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val AUTO_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
        val DISMISSED_UPDATE = stringPreferencesKey("dismissed_update_version")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = block(prefs.toSettings())
            prefs[Keys.MODE] = updated.identifierMode.name
            prefs[Keys.FIXED_ID] = updated.fixedIdentifier
            prefs[Keys.COLLECTION] = updated.defaultCollection
            prefs[Keys.MEDIATYPE] = updated.defaultMediatype
            prefs[Keys.WIFI_ONLY] = updated.wifiOnly
            prefs[Keys.CONCURRENT] = updated.concurrentUploads.coerceIn(1, 3)
            prefs[Keys.THEME] = updated.themeMode.name
            prefs[Keys.DYNAMIC] = updated.dynamicColor
            prefs[Keys.AMOLED] = updated.amoled
            prefs[Keys.LOCK] = updated.appLock
            prefs[Keys.ONBOARDING] = updated.onboardingDone
            prefs[Keys.WARNING] = updated.warningAccepted
            prefs[Keys.SYNC_URI] = updated.syncUri
            prefs[Keys.LAST_SYNC] = updated.lastSyncAt
            prefs[Keys.AUTO_EMPTY] = updated.autoRemoveEmptyFolders
            prefs[Keys.PAUSED] = updated.uploadsPaused
            prefs[Keys.CHARGING_ONLY] = updated.chargingOnly
            prefs[Keys.AUTO_UPDATES] = updated.autoCheckUpdates
            prefs[Keys.LAST_UPDATE_CHECK] = updated.lastUpdateCheckAt
            prefs[Keys.DISMISSED_UPDATE] = updated.dismissedUpdateVersion
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            identifierMode = this[Keys.MODE]?.let { runCatching { IdentifierMode.valueOf(it) }.getOrNull() }
                ?: defaults.identifierMode,
            fixedIdentifier = this[Keys.FIXED_ID] ?: defaults.fixedIdentifier,
            defaultCollection = this[Keys.COLLECTION] ?: defaults.defaultCollection,
            defaultMediatype = this[Keys.MEDIATYPE] ?: defaults.defaultMediatype,
            wifiOnly = this[Keys.WIFI_ONLY] ?: defaults.wifiOnly,
            concurrentUploads = (this[Keys.CONCURRENT] ?: defaults.concurrentUploads).coerceIn(1, 3),
            themeMode = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            dynamicColor = this[Keys.DYNAMIC] ?: defaults.dynamicColor,
            amoled = this[Keys.AMOLED] ?: defaults.amoled,
            appLock = this[Keys.LOCK] ?: defaults.appLock,
            onboardingDone = this[Keys.ONBOARDING] ?: defaults.onboardingDone,
            warningAccepted = this[Keys.WARNING] ?: defaults.warningAccepted,
            syncUri = this[Keys.SYNC_URI] ?: defaults.syncUri,
            lastSyncAt = this[Keys.LAST_SYNC] ?: defaults.lastSyncAt,
            autoRemoveEmptyFolders = this[Keys.AUTO_EMPTY] ?: defaults.autoRemoveEmptyFolders,
            uploadsPaused = this[Keys.PAUSED] ?: defaults.uploadsPaused,
            chargingOnly = this[Keys.CHARGING_ONLY] ?: defaults.chargingOnly,
            autoCheckUpdates = this[Keys.AUTO_UPDATES] ?: defaults.autoCheckUpdates,
            lastUpdateCheckAt = this[Keys.LAST_UPDATE_CHECK] ?: defaults.lastUpdateCheckAt,
            dismissedUpdateVersion = this[Keys.DISMISSED_UPDATE] ?: defaults.dismissedUpdateVersion
        )
    }
}

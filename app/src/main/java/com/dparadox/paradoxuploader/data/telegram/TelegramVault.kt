package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.domain.telegram.ApiCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Telegram API id/hash and the key that encrypts TDLib's on-disk database.
 * Everything lives in EncryptedSharedPreferences (AES-256, Android Keystore master key).
 */
@Singleton
class TelegramVault @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiCredentials(): ApiCredentials? {
        val id = runCatching { prefs.getInt(KEY_API_ID, 0) }.getOrDefault(0)
        val hash = runCatching { prefs.getString(KEY_API_HASH, null) }.getOrNull()
        if (id > 0 && !hash.isNullOrBlank()) return ApiCredentials(id, hash)
        if (BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()) {
            return ApiCredentials(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
        }
        return null
    }

    fun saveApiCredentials(credentials: ApiCredentials) {
        prefs.edit().putInt(KEY_API_ID, credentials.apiId).putString(KEY_API_HASH, credentials.apiHash.trim()).apply()
    }

    /** 32 random bytes, created once; never leaves the device and is never logged. */
    @Synchronized
    fun databaseKey(): ByteArray {
        prefs.getString(KEY_DB, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    private companion object {
        const val FILE = "paradox_tg_vault"
        const val KEY_API_ID = "api_id"
        const val KEY_API_HASH = "api_hash"
        const val KEY_DB = "db_key"
    }
}

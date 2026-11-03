package com.dparadox.paradoxuploader.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dparadox.paradoxuploader.domain.model.Credentials
import com.dparadox.paradoxuploader.domain.repository.KeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the S3 keys in EncryptedSharedPreferences backed by the Android Keystore. */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) : KeyStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun get(): Credentials? = try {
        val access = prefs.getString(KEY_ACCESS, null)
        val secret = prefs.getString(KEY_SECRET, null)
        if (access.isNullOrBlank() || secret.isNullOrBlank()) null else Credentials(access, secret)
    } catch (e: Exception) {
        null
    }

    override fun save(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_ACCESS, credentials.accessKey.trim())
            .putString(KEY_SECRET, credentials.secretKey.trim())
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "paradox_secure_keys"
        const val KEY_ACCESS = "access_key"
        const val KEY_SECRET = "secret_key"
    }
}

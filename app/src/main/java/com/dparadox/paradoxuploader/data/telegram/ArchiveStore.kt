package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dparadox.paradoxuploader.domain.telegram.ArchiveChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.archiveStore by preferencesDataStore(name = "telegram_archive")

/** Local references to archive channels. Holds chat ids and names only, never session data. */
@Singleton
class ArchiveStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("channels")

    val channels: Flow<List<ArchiveChannel>> =
        context.archiveStore.data.map { decode(it[key]) }

    /** Atomic read-modify-write. DataStore serialises concurrent edits. */
    suspend fun update(transform: (List<ArchiveChannel>) -> List<ArchiveChannel>) {
        context.archiveStore.edit { prefs ->
            prefs[key] = encode(transform(decode(prefs[key])))
        }
    }

    private fun encode(list: List<ArchiveChannel>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("chatId", it.chatId)
                    .put("title", it.title)
                    .put("isDefault", it.isDefault)
                    .put("isPublic", it.isPublic)
                    .put("createdAt", it.createdAt)
            )
        }
    }.toString()

    private fun decode(raw: String?): List<ArchiveChannel> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ArchiveChannel(
                    chatId = o.getLong("chatId"),
                    title = o.getString("title"),
                    isDefault = o.optBoolean("isDefault"),
                    isPublic = o.optBoolean("isPublic"),
                    createdAt = o.optLong("createdAt")
                )
            }
        }.getOrDefault(emptyList())
    }
}

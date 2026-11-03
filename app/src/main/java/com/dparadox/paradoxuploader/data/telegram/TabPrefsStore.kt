package com.dparadox.paradoxuploader.data.telegram

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.domain.telegram.DestinationKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class HomeTab { UPLOAD, DOWNLOAD }

private val Context.tabStore by preferencesDataStore(name = "telegram_tabs")

/** Default channel and pinned channels for each Telegram tab. Stores ids and names only. */
@Singleton
class TabPrefsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun defaultKey(tab: HomeTab) = stringPreferencesKey("default_${tab.name}")
    private fun pinsKey(tab: HomeTab) = stringPreferencesKey("pins_${tab.name}")

    fun defaultOf(tab: HomeTab): Flow<ChatDestination?> =
        context.tabStore.data.map { p -> p[defaultKey(tab)]?.let { decodeOne(it) } }

    fun pinsOf(tab: HomeTab): Flow<List<ChatDestination>> =
        context.tabStore.data.map { p -> decodeList(p[pinsKey(tab)]) }

    suspend fun setDefault(tab: HomeTab, dest: ChatDestination) {
        context.tabStore.edit { it[defaultKey(tab)] = encodeOne(dest).toString() }
    }

    suspend fun togglePin(tab: HomeTab, dest: ChatDestination) {
        context.tabStore.edit { p ->
            val list = decodeList(p[pinsKey(tab)]).toMutableList()
            val idx = list.indexOfFirst { it.chatId == dest.chatId }
            if (idx >= 0) list.removeAt(idx) else list.add(dest)
            p[pinsKey(tab)] = encodeList(list)
        }
    }

    private fun encodeOne(d: ChatDestination): JSONObject =
        JSONObject().put("id", d.chatId).put("t", d.title).put("k", d.kind.name)

    private fun decodeOne(raw: String): ChatDestination? = runCatching {
        val o = JSONObject(raw)
        ChatDestination(o.getLong("id"), o.getString("t"), DestinationKind.valueOf(o.getString("k")))
    }.getOrNull()

    private fun encodeList(list: List<ChatDestination>): String =
        JSONArray().apply { list.forEach { put(encodeOne(it)) } }.toString()

    private fun decodeList(raw: String?): List<ChatDestination> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> decodeOne(arr.getJSONObject(i).toString()) }.filterNotNull()
        }.getOrDefault(emptyList())
    }
}

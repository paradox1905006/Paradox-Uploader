package com.dparadox.paradoxuploader.data.telegram

import java.util.concurrent.ConcurrentHashMap

data class LinkEntry(
    val token: String,
    val fileId: Int,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val expiresAt: Long
)

/** Token -> file lookup for the loopback server. Entries are removed when they expire. */
class LinkRegistry {
    private val map = ConcurrentHashMap<String, LinkEntry>()

    fun put(entry: LinkEntry) { map[entry.token] = entry }

    fun get(token: String): LinkEntry? {
        val e = map[token] ?: return null
        if (System.currentTimeMillis() > e.expiresAt) {
            map.remove(token)
            return null
        }
        return e
    }

    fun remove(token: String) { map.remove(token) }

    fun clear() = map.clear()

    /** Drops expired entries and returns the number still valid. */
    fun sweep(): Int {
        val now = System.currentTimeMillis()
        map.entries.removeAll { it.value.expiresAt < now }
        return map.size
    }
}

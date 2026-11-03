package com.dparadox.paradoxuploader.domain.telegram

/**
 * Parses Telegram message links:
 *  - https://t.me/<username>/<id>            (public channel / group)
 *  - https://t.me/<username>/<topic>/<id>
 *  - https://t.me/c/<channelId>/<id>         (private channel / supergroup)
 *  - https://t.me/c/<channelId>/<topic>/<id>
 *  - same with telegram.me / telegram.dog / no scheme / http
 *  - tg://resolve?domain=<username>&post=<id>
 *  - tg://privatepost?channel=<channelId>&post=<id>
 */
object TelegramLinkParser {

    private val HOSTS = setOf("t.me", "www.t.me", "telegram.me", "www.telegram.me", "telegram.dog", "www.telegram.dog")
    private val USERNAME = Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$")
    private val RESERVED = setOf("c", "s", "joinchat", "addstickers", "addemoji", "addtheme", "share", "proxy", "socks", "login", "iv", "bg", "invoice", "boost", "m")

    fun parse(input: String): TgLink {
        val raw = input.trim()
        if (raw.isEmpty()) throw TelegramException(TgError.EmptyLink)
        return if (raw.startsWith("tg://", ignoreCase = true)) parseTg(raw) else parseWeb(raw)
    }

    fun parseOrNull(input: String): TgLink? = try {
        parse(input)
    } catch (e: TelegramException) {
        null
    }

    private fun parseTg(raw: String): TgLink {
        val rest = raw.substring(5)
        val host = rest.substringBefore('?').trim('/').lowercase()
        val query = queryMap(rest.substringAfter('?', ""))
        val post = query["post"]?.toLongOrNull()?.takeIf { it > 0 } ?: invalid()
        return when (host) {
            "resolve" -> {
                val user = query["domain"] ?: invalid()
                publicLink(user, post, "https://t.me/$user/$post")
            }
            "privatepost" -> {
                val channel = query["channel"]?.toLongOrNull()?.takeIf { it > 0 } ?: invalid()
                TgLink.Private(channel, post, "https://t.me/c/$channel/$post")
            }
            else -> invalid()
        }
    }

    private fun parseWeb(raw: String): TgLink {
        var s = raw
        val scheme = s.indexOf("://")
        if (scheme in 1..5) {
            val sch = s.substring(0, scheme).lowercase()
            if (sch != "http" && sch != "https") invalid()
            s = s.substring(scheme + 3)
        }
        val host = s.substringBefore('/').lowercase()
        if (host !in HOSTS) invalid()
        val path = s.substringAfter('/', "").substringBefore('?').substringBefore('#')
        val segs = path.split('/').filter { it.isNotEmpty() }
        if (segs.isEmpty()) invalid()
        if (segs[0].startsWith("+") || segs[0].equals("joinchat", true)) throw TelegramException(TgError.InviteLink)

        val numeric = { v: String -> v.length in 1..12 && v.all { it in '0'..'9' } }
        if (segs[0] == "c") {
            if (segs.size !in 3..4 || !segs.drop(1).all(numeric)) invalid()
            val channel = segs[1].toLong().takeIf { it > 0 } ?: invalid()
            val msg = segs.last().toLong().takeIf { it > 0 } ?: invalid()
            return TgLink.Private(channel, msg, "https://t.me/" + segs.joinToString("/"))
        }
        if (segs.size !in 2..3 || !segs.drop(1).all(numeric)) invalid()
        val msg = segs.last().toLong().takeIf { it > 0 } ?: invalid()
        return publicLink(segs[0], msg, "https://t.me/" + segs.joinToString("/"))
    }

    private fun publicLink(username: String, msg: Long, canonical: String): TgLink {
        if (!USERNAME.matches(username) || username.lowercase() in RESERVED) invalid()
        return TgLink.Public(username, msg, canonical)
    }

    private fun queryMap(q: String): Map<String, String> =
        q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()

    private fun invalid(): Nothing = throw TelegramException(TgError.InvalidLink)
}

package com.dparadox.paradoxuploader.domain.util

import java.net.URLEncoder

/** Encodes metadata values for `x-archive-meta-*` headers (non-ASCII values use `uri(...)`). */
object MetaHeaders {
    fun encode(value: String): String {
        val printableAscii = value.all { it.code in 0x20..0x7E }
        return if (printableAscii && !value.startsWith("uri(")) {
            value.trim()
        } else {
            "uri(" + URLEncoder.encode(value, "UTF-8").replace("+", "%20") + ")"
        }
    }
}

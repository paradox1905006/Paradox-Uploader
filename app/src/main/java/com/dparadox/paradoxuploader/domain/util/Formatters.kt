package com.dparadox.paradoxuploader.domain.util

import java.util.Locale

object Formatters {
    /** 1024-based, one decimal: 13,000,000 B -> "12.4 MB". */
    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(locale, "%.1f %s", value, units[index])
    }

    fun speed(bytesPerSecond: Long, locale: Locale = Locale.getDefault()): String =
        size(bytesPerSecond, locale) + "/s"

    fun eta(seconds: Long): String = when {
        seconds < 0 -> "—"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m %02ds".format(seconds % 60)
        else -> "${seconds / 3600}h %02dm".format((seconds % 3600) / 60)
    }
}

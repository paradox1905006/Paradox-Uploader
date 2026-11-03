package com.dparadox.paradoxuploader.domain.util

/** Compares version names such as "1.2.0" and "v1.10.1" (a leading "v" and any "-beta" suffix are ignored). */
object VersionCompare {
    fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}

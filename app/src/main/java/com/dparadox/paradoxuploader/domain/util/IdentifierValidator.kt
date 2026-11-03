package com.dparadox.paradoxuploader.domain.util

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

enum class IdentifierError { EMPTY, TOO_SHORT, TOO_LONG, INVALID_CHARS, INVALID_START }

/** Archive.org identifiers: lowercase letters, digits, '-' and '_' only. */
object IdentifierValidator {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 100
    private val ALLOWED = Regex("^[a-z0-9_-]+$")

    fun validate(identifier: String): IdentifierError? = when {
        identifier.isEmpty() -> IdentifierError.EMPTY
        !ALLOWED.matches(identifier) -> IdentifierError.INVALID_CHARS
        identifier.first() == '-' || identifier.first() == '_' -> IdentifierError.INVALID_START
        identifier.length < MIN_LENGTH -> IdentifierError.TOO_SHORT
        identifier.length > MAX_LENGTH -> IdentifierError.TOO_LONG
        else -> null
    }

    fun isValid(identifier: String): Boolean = validate(identifier) == null
}

object IdentifierGenerator {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val secure: Random = SecureRandom().asKotlinRandom()

    fun random(prefix: String, rng: Random = secure): String =
        prefix + (1..8).map { ALPHABET[rng.nextInt(ALPHABET.length)] }.joinToString("")

    /** "My Trip 2026" -> "my-trip-2026-k3x9qa". Non-latin names fall back to "paradox-folder-...". */
    fun forFolder(name: String, rng: Random = secure): String {
        val slug = name.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(50)
            .trim('-')
        val suffix = (1..6).map { ALPHABET[rng.nextInt(ALPHABET.length)] }.joinToString("")
        return if (slug.isEmpty()) "paradox-folder-$suffix" else "$slug-$suffix"
    }

    fun newItem(): String = random("paradox-")
    fun testItem(): String = random("paradox-test-")

    /** The same key always tests against the same item, so repeated tests leave at most one empty item behind. */
    fun testItemFor(accessKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(accessKey.toByteArray(Charsets.UTF_8))
        val hex = digest.take(5).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "paradox-test-$hex"
    }
}

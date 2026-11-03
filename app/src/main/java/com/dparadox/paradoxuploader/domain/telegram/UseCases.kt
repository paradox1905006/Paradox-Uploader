package com.dparadox.paradoxuploader.domain.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SignInWithPhone @Inject constructor(private val repo: TelegramAuthRepository) {
    suspend operator fun invoke(phone: String): TgResult<Unit> {
        val cleaned = phone.filter { it.isDigit() || it == '+' }
        if (cleaned.count { it.isDigit() } < 6) {
            return TgResult.Failure(TgError.AuthFailed(AuthFailure.PHONE_INVALID))
        }
        return repo.submitPhone(if (cleaned.startsWith("+")) cleaned else "+$cleaned")
    }
}

class SubmitLoginCode @Inject constructor(private val repo: TelegramAuthRepository) {
    suspend operator fun invoke(code: String): TgResult<Unit> = repo.submitCode(code.filter { it.isDigit() })
}

class SubmitTwoFactorPassword @Inject constructor(private val repo: TelegramAuthRepository) {
    suspend operator fun invoke(password: String): TgResult<Unit> = repo.submitPassword(password)
}

class LogoutFromTelegram @Inject constructor(
    private val repo: TelegramAuthRepository,
    private val links: DirectLinkRepository,
    private val session: ResolverSession
) {
    suspend operator fun invoke(): TgResult<Unit> {
        links.revokeAll()
        session.clear()
        return repo.logout()
    }
}

class ResolveTelegramLink @Inject constructor(
    private val resolver: TelegramResolverRepository,
    private val session: ResolverSession
) {
    suspend operator fun invoke(input: String): TgResult<ResolvedFile> {
        val link = try {
            TelegramLinkParser.parse(input)
        } catch (e: TelegramException) {
            return TgResult.Failure(e.error)
        }
        val result = resolver.resolve(link)
        if (result is TgResult.Success) session.setFile(result.value)
        return result
    }
}

class CreateDirectLink @Inject constructor(
    private val links: DirectLinkRepository,
    private val prefs: TelegramPrefs,
    private val session: ResolverSession
) {
    suspend operator fun invoke(file: ResolvedFile): TgResult<DirectLink> {
        var ttl = 60
        prefs.linkTtlMinutes.collectFirst { ttl = it }
        val result = links.create(file, ttl)
        if (result is TgResult.Success) session.setLink(result.value)
        return result
    }

    private suspend fun Flow<Int>.collectFirst(block: (Int) -> Unit) {
        block(first())
    }
}

class RevokeDirectLink @Inject constructor(
    private val links: DirectLinkRepository,
    private val session: ResolverSession
) {
    operator fun invoke(link: DirectLink) {
        links.revoke(link.token)
        session.setLink(null)
    }
}

class DownloadTelegramFile @Inject constructor(private val repo: FileDownloadRepository) {
    operator fun invoke(file: ResolvedFile): Flow<DownloadProgress> = repo.download(file)
}

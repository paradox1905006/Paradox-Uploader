package com.dparadox.paradoxuploader.domain.util

import com.dparadox.paradoxuploader.domain.model.ApiResult
import com.dparadox.paradoxuploader.domain.model.UpdateCheckResult
import com.dparadox.paradoxuploader.domain.model.UpdateInfo
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.UpdateService
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the user when a newer version of the app has been published. */
@Singleton
class UpdateChecker @Inject constructor(
    private val service: UpdateService,
    private val settings: SettingsRepository,
    private val clock: Clock
) {
    /** Always asks the server (used by the "Check for updates" button). */
    suspend fun check(currentVersion: String): UpdateCheckResult =
        when (val result = service.latest()) {
            is ApiResult.Failure -> UpdateCheckResult.Failed(result.message.takeIf { it.isNotBlank() })
            is ApiResult.Success -> {
                val now = clock.nowMs()
                settings.update { it.copy(lastUpdateCheckAt = now) }
                if (VersionCompare.isNewer(result.value.versionName, currentVersion)) {
                    UpdateCheckResult.Available(result.value)
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
        }

    /**
     * Automatic check on app start: at most once a day, only when enabled, and never again for a
     * version the user already chose "Later" for.
     */
    suspend fun checkIfDue(currentVersion: String): UpdateInfo? {
        val current = settings.current()
        if (!current.autoCheckUpdates) return null
        if (clock.nowMs() - current.lastUpdateCheckAt < CHECK_INTERVAL_MS) return null
        val result = check(currentVersion) as? UpdateCheckResult.Available ?: return null
        return result.info.takeIf { it.versionName != current.dismissedUpdateVersion }
    }

    suspend fun dismiss(version: String) {
        settings.update { it.copy(dismissedUpdateVersion = version) }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

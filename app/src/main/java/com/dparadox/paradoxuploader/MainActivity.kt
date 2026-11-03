package com.dparadox.paradoxuploader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.dparadox.paradoxuploader.domain.util.IncomingShares
import com.dparadox.paradoxuploader.notifications.AppIntents
import com.dparadox.paradoxuploader.ui.nav.DeepLink
import com.dparadox.paradoxuploader.ui.nav.MainViewModel
import com.dparadox.paradoxuploader.ui.nav.ParadoxRoot
import com.dparadox.paradoxuploader.ui.util.AppLockSupport
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var shares: IncomingShares

    private val mainViewModel: MainViewModel by viewModels()
    private var deepLink by mutableStateOf<DeepLink?>(null)
    private var authenticating = false
    private var backgroundedAt = 0L

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val sizeClass = calculateWindowSizeClass(this)
            ParadoxRoot(
                viewModel = mainViewModel,
                wide = sizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                deepLink = deepLink,
                onDeepLinkConsumed = { deepLink = null },
                authenticate = ::authenticate,
                canAuthenticate = { AppLockSupport.isAvailable(this) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!authenticating) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
            mainViewModel.lock()
        }
        backgroundedAt = 0L
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        // Consume the intent so a later re-creation of this activity cannot queue the same share again.
        intent.action = null
        when (action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { shares.push(listOf(it.toString())) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { list -> shares.push(list.map { it.toString() }) }
            }
            AppIntents.ACTION_OPEN_FILES -> {
                val id = intent.getLongExtra(AppIntents.EXTRA_FILE_ID, -1L)
                deepLink = DeepLink(id.takeIf { it >= 0 })
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        if (authenticating) return
        authenticating = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setAllowedAuthenticators(AppLockSupport.allowedAuthenticators())
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                }
            }
        )
        prompt.authenticate(info)
    }

    private companion object {
        const val RELOCK_AFTER_MS = 15_000L
    }
}

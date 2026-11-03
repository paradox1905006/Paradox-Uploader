package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthState

object TgRoutes {
    const val GATE = "tg_gate"
    const val LOGIN = "tg_login"
    const val OTP = "tg_otp"
    const val TWO_FA = "tg_2fa"
    const val RESOLVER = "tg_resolver"
    const val DETAILS = "tg_details"
    const val LINK = "tg_link"
    const val SETTINGS = "tg_settings"
    const val ARCHIVE = "tg_archive"
    const val CREATE_CHANNEL = "tg_create_channel"
    const val UPLOAD = "tg_upload"
    const val DESTINATIONS = "tg_destinations"
    const val EXPLORER = "tg_explorer/{chatId}"
    fun explorer(chatId: Long) = "tg_explorer/$chatId"

    val AUTH_ROUTES = setOf(GATE, LOGIN, OTP, TWO_FA)
}

/** Self-contained Telegram feature: owns its own NavController and follows the auth state. */
@Composable
fun TelegramRoot(gate: TelegramAuthViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val auth by gate.authState.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = TgRoutes.GATE) {
        composable(TgRoutes.GATE) { CenteredLoading(stringResource(R.string.tg_connecting)) }
        composable(TgRoutes.LOGIN) { LoginScreen() }
        composable(TgRoutes.OTP) { OtpScreen() }
        composable(TgRoutes.TWO_FA) { TwoFactorScreen() }
        composable(TgRoutes.RESOLVER) {
            ResolverScreen(
                onResolved = { nav.navigate(TgRoutes.DETAILS) },
                onOpenSettings = { nav.navigate(TgRoutes.SETTINGS) }
            )
        }
        composable(TgRoutes.DETAILS) {
            FileDetailsScreen(onBack = { nav.popBackStack() }, onGetLink = { nav.navigate(TgRoutes.LINK) })
        }
        composable(TgRoutes.LINK) { DownloadLinkScreen(onBack = { nav.popBackStack() }) }
        composable(TgRoutes.SETTINGS) { TelegramSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(TgRoutes.ARCHIVE) {
            TelegramHomeScreen(
                onOpenFiles = { nav.navigate(TgRoutes.explorer(it)) },
                onCreate = { nav.navigate(TgRoutes.CREATE_CHANNEL) },
                onPasteLink = { nav.navigate(TgRoutes.RESOLVER) }
            )
        }
        composable(TgRoutes.UPLOAD) {
            UploadDashboardScreen(
                onBack = { nav.popBackStack() },
                onChooseDestination = { nav.navigate(TgRoutes.DESTINATIONS) }
            )
        }
        composable(TgRoutes.DESTINATIONS) {
            UploadDestinationScreen(onBack = { nav.popBackStack() }, onPicked = { nav.popBackStack() })
        }
        composable(TgRoutes.CREATE_CHANNEL) {
            CreateCloudChannelScreen(onCreated = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable(
            TgRoutes.EXPLORER,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { ArchiveExplorerScreen(onBack = { nav.popBackStack() }) }
    }

    LaunchedEffect(auth) { followAuth(nav, auth) }
}

private fun followAuth(nav: NavHostController, auth: AuthState) {
    val current = nav.currentDestination?.route ?: TgRoutes.GATE
    val target = when (auth) {
        AuthState.Initializing, AuthState.LoggingOut -> TgRoutes.GATE
        AuthState.NeedsApiCredentials, AuthState.WaitPhone, is AuthState.Unsupported -> TgRoutes.LOGIN
        is AuthState.WaitCode -> TgRoutes.OTP
        is AuthState.WaitPassword -> TgRoutes.TWO_FA
        is AuthState.Ready -> if (current in TgRoutes.AUTH_ROUTES) TgRoutes.ARCHIVE else return
    }
    if (target == current) return
    nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

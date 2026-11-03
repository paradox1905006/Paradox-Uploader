package com.dparadox.paradoxuploader.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.ui.components.LocalSnackbarHost
import com.dparadox.paradoxuploader.ui.files.FilesScreen
import com.dparadox.paradoxuploader.ui.onboarding.NotificationRationaleScreen
import com.dparadox.paradoxuploader.ui.onboarding.OnboardingScreen
import com.dparadox.paradoxuploader.ui.settings.SettingsScreen
import com.dparadox.paradoxuploader.ui.telegram.TelegramRoot
import com.dparadox.paradoxuploader.ui.help.HelpScreen
import com.dparadox.paradoxuploader.ui.theme.ParadoxTheme
import com.dparadox.paradoxuploader.ui.upload.UploadScreen
import com.dparadox.paradoxuploader.ui.util.UiActions

object Routes {
    const val ONBOARDING = "onboarding"
    const val RATIONALE = "rationale"
    const val UPLOAD = "upload"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val TELEGRAM = "telegram"
}

private data class NavTarget(val route: String, val label: Int, val icon: ImageVector)

private val NAV_TARGETS = listOf(
    NavTarget(Routes.UPLOAD, R.string.nav_upload, Icons.Rounded.CloudUpload),
    NavTarget(Routes.FILES, R.string.nav_files, Icons.Rounded.Folder),
    NavTarget(Routes.TELEGRAM, R.string.nav_telegram, Icons.AutoMirrored.Rounded.Send),
    NavTarget(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings)
)
private val MAIN_ROUTES = NAV_TARGETS.map { it.route }.toSet()

@Composable
fun ParadoxRoot(
    viewModel: MainViewModel,
    wide: Boolean,
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    authenticate: (onSuccess: () -> Unit) -> Unit,
    canAuthenticate: () -> Boolean
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val current = settings

    ParadoxTheme(current ?: AppSettings()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (current == null) return@Surface
            if (current.appLock && locked && canAuthenticate()) {
                LockScreen(authenticate = authenticate, onUnlocked = viewModel::unlock)
                return@Surface
            }
            MainContent(
                viewModel = viewModel,
                settings = current,
                wide = wide,
                deepLink = deepLink,
                onDeepLinkConsumed = onDeepLinkConsumed
            )
        }
    }
}

@Composable
private fun MainContent(
    viewModel: MainViewModel,
    settings: AppSettings,
    wide: Boolean,
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = route in MAIN_ROUTES
    var scrollToId by remember { mutableStateOf<Long?>(null) }
    val update by viewModel.update.collectAsStateWithLifecycle()

    // In-app fallback when notifications are unavailable.
    LaunchedEffect(Unit) {
        viewModel.busEvents.collect { message ->
            snackbar.showSnackbar(context.getString(message.textRes, *message.args.toTypedArray()))
        }
    }

    LaunchedEffect(deepLink, settings.onboardingDone) {
        if (deepLink != null && settings.onboardingDone) {
            scrollToId = deepLink.fileId
            navController.navigateTop(Routes.FILES)
            onDeepLinkConsumed()
        }
    }

    CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
        Row(Modifier.fillMaxSize()) {
            if (wide && showNav) {
                NavigationRail {
                    NAV_TARGETS.forEach { target ->
                        NavigationRailItem(
                            selected = route == target.route,
                            onClick = { navController.navigateTop(target.route) },
                            icon = { Icon(target.icon, contentDescription = null) },
                            label = { Text(stringResource(target.label)) }
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide && showNav) {
                        NavigationBar {
                            NAV_TARGETS.forEach { target ->
                                NavigationBarItem(
                                    selected = route == target.route,
                                    onClick = { navController.navigateTop(target.route) },
                                    icon = { Icon(target.icon, contentDescription = null) },
                                    label = { Text(stringResource(target.label)) }
                                )
                            }
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0)
            ) { padding ->
                val insetsModifier = if (wide) Modifier.navigationBarsPadding() else Modifier
                NavHost(
                    navController = navController,
                    startDestination = if (settings.onboardingDone) Routes.UPLOAD else Routes.ONBOARDING,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .then(insetsModifier)
                        .imePadding()
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            viewModel.finishOnboarding()
                            val needsRationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            val next = if (needsRationale) Routes.RATIONALE else Routes.UPLOAD
                            navController.navigate(next) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.RATIONALE) {
                        NotificationRationaleScreen(onDone = {
                            navController.navigate(Routes.UPLOAD) { popUpTo(Routes.RATIONALE) { inclusive = true } }
                        })
                    }
                    composable(Routes.UPLOAD) {
                        UploadScreen(onOpenSettings = { navController.navigateTop(Routes.SETTINGS) })
                    }
                    composable(Routes.FILES) {
                        FilesScreen(scrollToId = scrollToId, onScrollHandled = { scrollToId = null })
                    }
                    composable(Routes.TELEGRAM) { TelegramRoot() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onOpenHelp = { navController.navigate(Routes.HELP) })
                    }
                    composable(Routes.HELP) {
                        HelpScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_body, info.versionName))
                    if (info.notes.isNotBlank()) {
                        Text(info.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    UiActions.open(context, info.url)
                    viewModel.dismissUpdate()
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdate) { Text(stringResource(R.string.update_later)) }
            }
        )
    }

    if (showNav && !settings.warningAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(stringResource(R.string.warning_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::acceptWarning) { Text(stringResource(R.string.warning_accept)) }
            }
        )
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(Routes.UPLOAD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LockScreen(authenticate: (onSuccess: () -> Unit) -> Unit, onUnlocked: () -> Unit) {
    LaunchedEffect(Unit) { authenticate(onUnlocked) }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_paradox),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.size(96.dp)
        )
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(onClick = { authenticate(onUnlocked) }, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.unlock))
        }
    }
}

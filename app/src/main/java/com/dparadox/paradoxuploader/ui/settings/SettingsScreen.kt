@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.ErrorKind
import com.dparadox.paradoxuploader.domain.model.HistoryResult
import com.dparadox.paradoxuploader.domain.model.IdentifierMode
import com.dparadox.paradoxuploader.domain.model.ThemeMode
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.domain.util.IdentifierError
import com.dparadox.paradoxuploader.domain.util.IdentifierValidator
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.GroupLabel
import com.dparadox.paradoxuploader.ui.components.HeroCard
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.ListRow
import com.dparadox.paradoxuploader.ui.components.LocalSnackbarHost
import com.dparadox.paradoxuploader.ui.components.LogoMark
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.ParadoxScaffold
import com.dparadox.paradoxuploader.ui.components.ScreenColumn
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.SwitchRow
import com.dparadox.paradoxuploader.ui.components.TonePill
import com.dparadox.paradoxuploader.ui.util.AppLockSupport
import com.dparadox.paradoxuploader.ui.util.UiActions
import com.dparadox.paradoxuploader.ui.util.formatDateTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MEDIATYPES = listOf("data", "texts", "movies", "audio", "image", "software")

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ParadoxScaffold(
        title = stringResource(R.string.nav_settings),
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        ScreenColumn(padding, spacing = Spacing.sm) {
            SettingsHeader(hasKeys = viewModel.hasSavedKeys)

            KeysSection(viewModel)

            Section(stringResource(R.string.settings_upload)) {
                Text(stringResource(R.string.identifier_mode), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        IdentifierMode.NEW_PER_FILE to R.string.mode_new_per_file,
                        IdentifierMode.FIXED to R.string.mode_fixed
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.identifierMode == mode,
                            onClick = { viewModel.update { it.copy(identifierMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                        ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (settings.identifierMode == IdentifierMode.FIXED) {
                    var fixedId by rememberSaveable { mutableStateOf(settings.fixedIdentifier) }
                    val error = IdentifierValidator.validate(fixedId)
                    OutlinedTextField(
                        value = fixedId,
                        onValueChange = { value ->
                            fixedId = value
                            viewModel.update { it.copy(fixedIdentifier = value) }
                        },
                        label = { Text(stringResource(R.string.fixed_identifier)) },
                        isError = error != null,
                        supportingText = {
                            Text(
                                if (error == null) stringResource(R.string.identifier_rules)
                                else stringResource(identifierErrorRes(error))
                            )
                        },
                        singleLine = true,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                var collection by rememberSaveable { mutableStateOf(settings.defaultCollection) }
                OutlinedTextField(
                    value = collection,
                    onValueChange = { value ->
                        collection = value
                        viewModel.update { it.copy(defaultCollection = value.trim()) }
                    },
                    label = { Text(stringResource(R.string.default_collection)) },
                    supportingText = { Text(stringResource(R.string.collection_hint)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.default_mediatype), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    MEDIATYPES.forEach { type ->
                        val selected = settings.defaultMediatype == type
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.update { it.copy(defaultMediatype = type) } },
                            label = { Text(type) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            shape = CircleShape
                        )
                    }
                }
                SettingSwitch(
                    title = stringResource(R.string.wifi_only),
                    subtitle = stringResource(R.string.wifi_only_hint),
                    checked = settings.wifiOnly,
                    onChecked = viewModel::setWifiOnly,
                    icon = Icons.Rounded.Wifi
                )
                SettingSwitch(
                    title = stringResource(R.string.charging_only),
                    subtitle = stringResource(R.string.charging_only_hint),
                    checked = settings.chargingOnly,
                    onChecked = viewModel::setChargingOnly,
                    icon = Icons.Rounded.BatteryChargingFull
                )
                var slider by remember(settings.concurrentUploads) {
                    mutableFloatStateOf(settings.concurrentUploads.toFloat())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    IconTile(
                        Icons.Rounded.Speed,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        circle = true
                    )
                    Text(
                        stringResource(R.string.concurrent_uploads, slider.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = {
                        viewModel.update { it.copy(concurrentUploads = slider.roundToInt()) }
                    },
                    valueRange = 1f..3f,
                    steps = 1
                )
            }

            HistorySection(viewModel, settings.syncUri, settings.lastSyncAt)

            CleanupSection(viewModel, settings.autoRemoveEmptyFolders)

            Section(stringResource(R.string.settings_appearance)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val themes = listOf(
                        Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
                        Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.BrightnessAuto)
                    )
                    themes.forEachIndexed { index, (mode, label, icon) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.update { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = settings.themeMode == mode) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                                }
                            }
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SettingSwitch(
                    title = stringResource(R.string.dynamic_color),
                    subtitle = stringResource(
                        if (dynamicSupported) R.string.dynamic_color_hint else R.string.dynamic_color_unsupported
                    ),
                    checked = settings.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onChecked = { value -> viewModel.update { it.copy(dynamicColor = value) } },
                    icon = Icons.Rounded.Palette
                )
                SettingSwitch(
                    title = stringResource(R.string.amoled_black),
                    subtitle = stringResource(R.string.amoled_hint),
                    checked = settings.amoled,
                    onChecked = { value -> viewModel.update { it.copy(amoled = value) } },
                    icon = Icons.Rounded.Contrast
                )
            }

            Section(stringResource(R.string.settings_security)) {
                SettingSwitch(
                    title = stringResource(R.string.app_lock),
                    subtitle = stringResource(R.string.app_lock_hint),
                    checked = settings.appLock,
                    onChecked = { value ->
                        if (value && !AppLockSupport.isAvailable(context)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.app_lock_unavailable)) }
                        } else {
                            viewModel.update { it.copy(appLock = value) }
                        }
                    },
                    icon = Icons.Rounded.Fingerprint
                )
            }

            Section(stringResource(R.string.settings_background)) {
                ListRow(
                    title = stringResource(R.string.battery_button),
                    subtitle = stringResource(R.string.battery_hint),
                    icon = Icons.Rounded.BatterySaver,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    trailing = { ChevronIcon() }
                )
            }

            UpdatesSection(viewModel, settings.autoCheckUpdates)

            Section(stringResource(R.string.settings_about)) {
                ListRow(
                    title = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                    subtitle = stringResource(R.string.privacy_note),
                    icon = Icons.Rounded.Info
                )
                ListRow(
                    title = stringResource(R.string.archive_terms),
                    icon = Icons.Rounded.Gavel,
                    onClick = { UiActions.open(context, ArchiveUrls.TERMS_PAGE) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                ListRow(
                    title = "Help, terms & how to use",
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    onClick = onOpenHelp,
                    trailing = { ChevronIcon() }
                )
            }
        }
    }
}

/** Profile-style header: logo, app name, version and key status. */
@Composable
private fun SettingsHeader(hasKeys: Boolean) {
    val scheme = MaterialTheme.colorScheme
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            LogoMark(size = 64.dp, container = scheme.primary, tint = scheme.onPrimary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.settings_header_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = 4.dp)) {
                    TonePill(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME),
                        container = scheme.surface.copy(alpha = 0.7f),
                        content = scheme.onSurface,
                        dot = false
                    )
                    TonePill(
                        text = stringResource(if (hasKeys) R.string.keys_status_ready else R.string.keys_status_missing),
                        container = if (hasKeys) scheme.tertiary else scheme.error,
                        content = if (hasKeys) scheme.onTertiary else scheme.onError
                    )
                }
            }
        }
    }
}

@Composable
private fun ChevronIcon() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun identifierErrorRes(error: IdentifierError): Int = when (error) {
    IdentifierError.EMPTY -> R.string.identifier_empty
    IdentifierError.TOO_SHORT -> R.string.identifier_too_short
    IdentifierError.TOO_LONG -> R.string.identifier_too_long
    IdentifierError.INVALID_CHARS -> R.string.identifier_invalid_chars
    IdentifierError.INVALID_START -> R.string.identifier_invalid_start
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        GroupLabel(title)
        ParadoxCard(spacing = Spacing.md) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    SwitchRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onChecked = onChecked,
        enabled = enabled,
        icon = icon
    )
}

@Composable
private fun KeysSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val state = viewModel.keyState
    val transformation = if (viewModel.showKeys) VisualTransformation.None else PasswordVisualTransformation()
    val keyboard = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)

    Section(stringResource(R.string.settings_keys)) {
        val saved = viewModel.hasSavedKeys
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(
                if (saved) Icons.Rounded.VerifiedUser else Icons.Rounded.Key,
                container = if (saved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                content = if (saved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                circle = true
            )
            Text(
                stringResource(if (saved) R.string.keys_saved else R.string.keys_not_saved),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = viewModel.accessKey,
            onValueChange = { viewModel.accessKey = it },
            label = { Text(stringResource(R.string.access_key)) },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = keyboard,
            shape = FieldShape,
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.secretKey,
            onValueChange = { viewModel.secretKey = it },
            label = { Text(stringResource(R.string.secret_key)) },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = keyboard,
            shape = FieldShape,
            leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { viewModel.showKeys = !viewModel.showKeys }) {
                    Icon(
                        imageVector = if (viewModel.showKeys) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (viewModel.showKeys) R.string.hide_keys else R.string.show_keys
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (state is KeyUiState.Testing) ParadoxProgress(progress = null)
        KeyStatusText(state)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Button(onClick = viewModel::saveKeys, enabled = state !is KeyUiState.Testing) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.save))
            }
            FilledTonalButton(onClick = viewModel::testKeys, enabled = state !is KeyUiState.Testing) {
                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.test_key))
            }
            TextButton(
                onClick = viewModel::removeKeys,
                enabled = state !is KeyUiState.Testing,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove_keys))
            }
        }
        ListRow(
            title = stringResource(R.string.get_keys),
            icon = Icons.Rounded.Link,
            onClick = { UiActions.open(context, ArchiveUrls.KEYS_PAGE) },
            trailing = {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
    }

    if (state is KeyUiState.AskSaveAnyway) {
        AlertDialog(
            onDismissRequest = viewModel::dismissKeyDialog,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.test_failed_title)) },
            text = { Text(keyErrorText(state.kind, state.message)) },
            confirmButton = { TextButton(onClick = viewModel::saveAnyway) { Text(stringResource(R.string.save_anyway)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissKeyDialog) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun KeyStatusText(state: KeyUiState) {
    val error = MaterialTheme.colorScheme.error
    val ok = MaterialTheme.colorScheme.secondary
    when (state) {
        is KeyUiState.TestPassed -> Text(
            stringResource(if (state.cleanedUp) R.string.test_passed else R.string.test_passed_no_cleanup),
            color = ok
        )
        is KeyUiState.TestFailed -> Text(keyErrorText(state.kind, state.message), color = error)
        KeyUiState.Saved -> Text(stringResource(R.string.keys_saved_now), color = ok)
        KeyUiState.Removed -> Text(stringResource(R.string.keys_removed))
        KeyUiState.MissingInput -> Text(stringResource(R.string.keys_missing_input), color = error)
        else -> Unit
    }
}

@Composable
private fun keyErrorText(kind: ErrorKind, message: String): String {
    val base = when (kind) {
        ErrorKind.AUTH -> stringResource(R.string.error_auth)
        ErrorKind.NETWORK -> stringResource(R.string.error_network)
        ErrorKind.IDENTIFIER -> stringResource(R.string.error_identifier)
        ErrorKind.SLOW_DOWN -> stringResource(R.string.error_slow_down)
        ErrorKind.SERVER -> stringResource(R.string.error_server)
        else -> stringResource(R.string.error_unknown)
    }
    return if (message.isBlank()) base else "$base ($message)"
}

/** Asks for read and write access that survives restarts, for a user-chosen sync file. */
private class OpenDocumentReadWrite : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}

private class CreateDocumentReadWrite : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}

private fun persistReadWrite(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (e: Exception) {
        // the provider did not offer a persistable grant; the sync still works while the grant lasts
    }
}

@Composable
private fun HistorySection(viewModel: SettingsViewModel, syncUri: String, lastSyncAt: Long) {
    val context = LocalContext.current
    val busy = viewModel.historyBusy

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportHistory(uri.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importHistory(uri.toString())
    }
    val createSyncLauncher = rememberLauncherForActivityResult(CreateDocumentReadWrite()) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.chooseSyncFile(uri.toString())
        }
    }
    val existingSyncLauncher = rememberLauncherForActivityResult(OpenDocumentReadWrite()) { uri ->
        if (uri != null) {
            persistReadWrite(context, uri)
            viewModel.chooseSyncFile(uri.toString())
        }
    }

    Section(stringResource(R.string.settings_history)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(
                Icons.Rounded.History,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                circle = true
            )
            Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { exportLauncher.launch("paradox-history.json") }, enabled = !busy) {
                Text(stringResource(R.string.history_export))
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(stringResource(R.string.history_import))
            }
        }

        Text(stringResource(R.string.history_sync_title), style = MaterialTheme.typography.titleSmall)
        if (syncUri.isBlank()) {
            Text(
                stringResource(R.string.history_sync_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { createSyncLauncher.launch("paradox-history.json") }, enabled = !busy) {
                    Text(stringResource(R.string.history_sync_create))
                }
                OutlinedButton(onClick = { existingSyncLauncher.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(stringResource(R.string.history_sync_existing))
                }
            }
        } else {
            Text(
                if (lastSyncAt > 0) {
                    stringResource(R.string.history_last_sync, formatDateTime(lastSyncAt))
                } else {
                    stringResource(R.string.history_never_synced)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = viewModel::syncNow, enabled = !busy) {
                    Text(stringResource(R.string.history_sync_now))
                }
                TextButton(onClick = viewModel::stopSync, enabled = !busy) {
                    Text(stringResource(R.string.history_sync_stop))
                }
            }
        }

        OutlinedButton(onClick = viewModel::syncWithArchive, enabled = !busy) {
            Text(stringResource(R.string.history_sync_archive))
        }

        OutlinedTextField(
            value = viewModel.archiveInput,
            onValueChange = { viewModel.archiveInput = it },
            label = { Text(stringResource(R.string.history_add_label)) },
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = viewModel::addFromArchive,
            enabled = !busy && viewModel.archiveInput.isNotBlank()
        ) { Text(stringResource(R.string.history_add_button)) }

        if (busy) ParadoxProgress(progress = null)
        viewModel.historyResult?.let { (action, result) ->
            val failed = result !is HistoryResult.Success
            Text(
                historyText(action, result),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun historyText(action: HistoryAction, result: HistoryResult): String = when (result) {
    is HistoryResult.Success -> when (action) {
        HistoryAction.EXPORT -> stringResource(R.string.history_exported, result.total)
        HistoryAction.IMPORT -> stringResource(R.string.history_imported, result.added)
        HistoryAction.SYNC_FILE -> stringResource(R.string.history_synced, result.added, result.total)
        HistoryAction.SYNC_ARCHIVE, HistoryAction.ADD_ITEM -> stringResource(R.string.history_archive_added, result.added)
    }
    HistoryResult.InvalidFile -> stringResource(R.string.history_invalid_file)
    HistoryResult.ReadFailed -> stringResource(R.string.history_read_failed)
    HistoryResult.WriteFailed -> stringResource(R.string.history_write_failed)
    is HistoryResult.Remote -> keyErrorText(result.kind, "")
}

@Composable
private fun CleanupSection(viewModel: SettingsViewModel, autoRemove: Boolean) {
    val context = LocalContext.current
    val emptyFolders by viewModel.emptyFolderCount.collectAsStateWithLifecycle()
    val scan = viewModel.scanResult

    Section(stringResource(R.string.settings_cleanup)) {
        SettingSwitch(
            title = stringResource(R.string.cleanup_auto),
            subtitle = stringResource(R.string.cleanup_auto_hint),
            checked = autoRemove,
            onChecked = { value -> viewModel.update { it.copy(autoRemoveEmptyFolders = value) } },
            icon = Icons.Rounded.CleaningServices
        )
        Text(stringResource(R.string.cleanup_empty_folders, emptyFolders), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = viewModel::removeEmptyFolders, enabled = emptyFolders > 0) {
            Text(stringResource(R.string.cleanup_remove_now))
        }
        viewModel.removedFolders?.let {
            Text(stringResource(R.string.cleanup_removed, it), color = MaterialTheme.colorScheme.secondary)
        }

        Text(stringResource(R.string.cleanup_archive_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.cleanup_archive_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = viewModel::scanEmptyItems, enabled = !viewModel.scanBusy) {
            Text(stringResource(R.string.cleanup_scan))
        }
        if (viewModel.scanBusy) ParadoxProgress(progress = null)
        if (scan != null) {
            Text(stringResource(R.string.cleanup_scan_count, scan.empty.size), style = MaterialTheme.typography.bodyMedium)
            scan.empty.forEach { identifier ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        identifier,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { UiActions.open(context, ArchiveUrls.detailsUrl(identifier)) }) {
                        Text(stringResource(R.string.open))
                    }
                    TextButton(onClick = { viewModel.dismissEmptyItem(identifier) }) {
                        Text(stringResource(R.string.cleanup_done))
                    }
                }
            }
            if (scan.failed > 0) {
                Text(
                    stringResource(R.string.cleanup_scan_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        TextButton(onClick = { UiActions.open(context, ArchiveUrls.REMOVE_ITEMS_HELP) }) {
            Text(stringResource(R.string.cleanup_guide))
        }
    }
}

@Composable
private fun UpdatesSection(viewModel: SettingsViewModel, autoCheck: Boolean) {
    val context = LocalContext.current
    val state = viewModel.updateState
    val downloadState = viewModel.updateDownloadState
    var showDownloadChoice by remember { mutableStateOf(false) }

    Section(stringResource(R.string.settings_updates)) {
        SettingSwitch(
            title = stringResource(R.string.update_auto),
            subtitle = stringResource(R.string.update_auto_hint),
            checked = autoCheck,
            onChecked = { value -> viewModel.update { it.copy(autoCheckUpdates = value) } },
            icon = Icons.Rounded.Autorenew
        )
        FilledTonalButton(onClick = viewModel::checkForUpdates, enabled = state !is UpdateUiState.Checking) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.update_check))
        }
        if (state is UpdateUiState.Checking) {
            ParadoxProgress(progress = null)
            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodySmall)
        }
        when (state) {
            UpdateUiState.UpToDate -> Text(
                stringResource(R.string.update_up_to_date),
                color = MaterialTheme.colorScheme.secondary
            )
            is UpdateUiState.Failed -> Column {
                Text(
                    stringResource(R.string.update_failed),
                    color = MaterialTheme.colorScheme.error
                )
                state.reason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UpdateUiState.Available -> {
                Text(
                    stringResource(R.string.update_body, state.info.versionName),
                    style = MaterialTheme.typography.titleSmall
                )
                if (state.info.notes.isNotBlank()) {
                    Text(state.info.notes, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { showDownloadChoice = true },
                    enabled = downloadState !is UpdateDownloadUiState.Downloading
                ) {
                    Text(stringResource(R.string.update_download))
                }
                if (downloadState is UpdateDownloadUiState.Downloading) {
                    ParadoxProgress(progress = null)
                    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodySmall)
                }
                if (downloadState is UpdateDownloadUiState.Failed) {
                    Text(
                        stringResource(R.string.update_download_failed, downloadState.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (showDownloadChoice) {
                    AlertDialog(
                        onDismissRequest = { showDownloadChoice = false },
                        title = { Text(stringResource(R.string.update_download_choice_title)) },
                        text = {
                            Column {
                                TextButton(onClick = {
                                    showDownloadChoice = false
                                    UiActions.open(context, state.info.url)
                                }) {
                                    Column {
                                        Text(stringResource(R.string.update_download_github))
                                        Text(
                                            stringResource(R.string.update_download_github_hint),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                val apkUrl = state.info.apkUrl
                                TextButton(
                                    onClick = {
                                        showDownloadChoice = false
                                        if (apkUrl != null) viewModel.downloadUpdateInApp(context, apkUrl)
                                    },
                                    enabled = apkUrl != null
                                ) {
                                    Column {
                                        Text(stringResource(R.string.update_download_in_app))
                                        Text(
                                            stringResource(R.string.update_download_in_app_hint),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showDownloadChoice = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }
            else -> Unit
        }
    }
}

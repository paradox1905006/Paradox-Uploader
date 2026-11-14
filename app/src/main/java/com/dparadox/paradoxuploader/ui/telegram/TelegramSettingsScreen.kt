@file:OptIn(ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.telegram

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.ui.components.BannerTone
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InfoBanner
import com.dparadox.paradoxuploader.ui.components.InitialAvatar
import com.dparadox.paradoxuploader.ui.components.ListRow
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.Spacing

@Composable
fun TelegramSettingsScreen(onBack: () -> Unit, vm: TelegramSettingsViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ttl by vm.ttl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var showApi by rememberSaveable { mutableStateOf(false) }
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by rememberSaveable { mutableStateOf("") }
    var apiError by rememberSaveable { mutableStateOf<TgError?>(null) }

    LaunchedEffect(Unit) {
        vm.cacheCleared.collect { err ->
            if (err == null) Toast.makeText(context, R.string.tg_settings_cache_done, Toast.LENGTH_SHORT).show()
        }
    }

    TgScaffold(stringResource(R.string.tg_settings_title), onBack = onBack) { padding ->
        TgForm(padding) {
            val scheme = MaterialTheme.colorScheme
            val ready = auth as? AuthState.Ready
            ParadoxCard(spacing = Spacing.md) {
                Text(stringResource(R.string.tg_settings_account), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                if (ready == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IconTile(
                            Icons.Rounded.PersonOff,
                            size = 56.dp,
                            container = scheme.surfaceContainerHighest,
                            content = scheme.onSurfaceVariant,
                            circle = true
                        )
                        Text(stringResource(R.string.tg_settings_not_signed_in), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    ready.account?.let { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            InitialAvatar(a.displayName, size = 64.dp, container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a.displayName, style = MaterialTheme.typography.titleLarge)
                                listOfNotNull(a.username?.let { "@$it" }, a.phone).forEach {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmLogout = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tg_settings_logout))
                    }
                }
            }

            ParadoxCard(spacing = Spacing.md) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    IconTile(
                        Icons.Rounded.Timer,
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        circle = true
                    )
                    Text(stringResource(R.string.tg_settings_ttl), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.ttlOptions.forEach { minutes ->
                        val selected = ttl == minutes
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setTtl(minutes) },
                            label = { Text(stringResource(R.string.tg_settings_ttl_value, minutes)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            shape = CircleShape
                        )
                    }
                }
            }

            ParadoxCard(spacing = Spacing.md) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    IconTile(
                        Icons.Rounded.CleaningServices,
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        circle = true
                    )
                    Text(
                        stringResource(R.string.tg_settings_cache_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                FilledTonalButton(onClick = vm::clearCache, enabled = auth is AuthState.Ready) {
                    Text(stringResource(R.string.tg_settings_cache))
                }
            }

            ParadoxCard(spacing = Spacing.md) {
                ListRow(
                    title = stringResource(R.string.tg_settings_api),
                    icon = Icons.Rounded.Key,
                    onClick = { showApi = !showApi },
                    trailing = {
                        Icon(
                            if (showApi) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant
                        )
                    }
                )
                if (showApi) {
                    OutlinedTextField(apiId, { apiId = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.tg_api_id_label)) }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(apiHash, { apiHash = it.trim() }, label = { Text(stringResource(R.string.tg_api_hash_label)) }, singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.saveApi(apiId, apiHash) { apiError = it; if (it == null) showApi = false } }) {
                        Text(stringResource(R.string.tg_api_save))
                    }
                    apiError?.let { ErrorCard(it) }
                }
            }

            InfoBanner(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.tg_settings_about),
                tone = BannerTone.ACCENT
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.tg_settings_logout_title)) },
            text = { Text(stringResource(R.string.tg_settings_logout_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.tg_settings_logout)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.tg_cancel)) } }
        )
    }
}

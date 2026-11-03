@file:OptIn(ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.telegram

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.domain.telegram.TgError

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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tg_settings_account), style = MaterialTheme.typography.titleSmall)
                    val ready = auth as? AuthState.Ready
                    if (ready == null) {
                        Text(stringResource(R.string.tg_settings_not_signed_in))
                    } else {
                        ready.account?.let { a ->
                            Text(a.displayName, style = MaterialTheme.typography.titleMedium)
                            listOfNotNull(a.username?.let { "@$it" }, a.phone).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Button(
                            onClick = { confirmLogout = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.tg_settings_logout)) }
                    }
                }
            }

            Text(stringResource(R.string.tg_settings_ttl), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.ttlOptions.forEach { minutes ->
                    FilterChip(
                        selected = ttl == minutes,
                        onClick = { vm.setTtl(minutes) },
                        label = { Text(stringResource(R.string.tg_settings_ttl_value, minutes)) }
                    )
                }
            }

            Text(stringResource(R.string.tg_settings_cache_body), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = vm::clearCache, enabled = auth is AuthState.Ready) {
                Text(stringResource(R.string.tg_settings_cache))
            }

            OutlinedButton(onClick = { showApi = !showApi }) { Text(stringResource(R.string.tg_settings_api)) }
            if (showApi) {
                OutlinedTextField(apiId, { apiId = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.tg_api_id_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiHash, { apiHash = it.trim() }, label = { Text(stringResource(R.string.tg_api_hash_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveApi(apiId, apiHash) { apiError = it; if (it == null) showApi = false } }) {
                    Text(stringResource(R.string.tg_api_save))
                }
                apiError?.let { ErrorCard(it) }
            }

            Text(stringResource(R.string.tg_settings_about), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.tg_settings_logout_title)) },
            text = { Text(stringResource(R.string.tg_settings_logout_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.tg_settings_logout)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.tg_cancel)) } }
        )
    }
}

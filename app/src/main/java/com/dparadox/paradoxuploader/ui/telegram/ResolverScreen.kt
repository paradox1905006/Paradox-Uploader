package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.domain.telegram.ConnectionStatus

@Composable
fun ResolverScreen(
    onResolved: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ResolverViewModel = hiltViewModel()
) {
    val input by vm.input.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { vm.opened.collect { onResolved() } }

    TgScaffold(
        title = stringResource(R.string.tg_resolver_title),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.tg_open_settings))
            }
        }
    ) { padding ->
        TgForm(padding) {
            (auth as? AuthState.Ready)?.account?.displayName?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.tg_signed_in_as, it), style = MaterialTheme.typography.labelLarge)
            }
            when (connection) {
                ConnectionStatus.CONNECTING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InlineSpinner(); Text(stringResource(R.string.tg_connecting), style = MaterialTheme.typography.bodySmall)
                }
                ConnectionStatus.WAITING_FOR_NETWORK ->
                    Text(stringResource(R.string.tg_waiting_network), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                ConnectionStatus.CONNECTED -> Unit
            }
            Text(stringResource(R.string.tg_resolver_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = input,
                onValueChange = vm::setInput,
                label = { Text(stringResource(R.string.tg_link_label)) },
                placeholder = { Text(stringResource(R.string.tg_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = ui is ResolveUi.Error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.resolve() }),
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let(vm::setInput) }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.tg_paste))
                    }
                }
            )
            Button(onClick = vm::resolve, enabled = ui != ResolveUi.Loading, modifier = Modifier.fillMaxWidth()) {
                if (ui == ResolveUi.Loading) {
                    InlineSpinner()
                    Text(stringResource(R.string.tg_resolving), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.tg_resolve))
                }
            }
            (ui as? ResolveUi.Error)?.let { ErrorCard(it.error) }
        }
    }
}

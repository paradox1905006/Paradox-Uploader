package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
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
import com.dparadox.paradoxuploader.ui.components.FieldShape
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.TonePill

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
            val scheme = MaterialTheme.colorScheme
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                (auth as? AuthState.Ready)?.account?.displayName?.takeIf { it.isNotBlank() }?.let {
                    TonePill(
                        text = stringResource(R.string.tg_signed_in_as, it),
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer
                    )
                }
                when (connection) {
                    ConnectionStatus.CONNECTING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        InlineSpinner(); Text(stringResource(R.string.tg_connecting), style = MaterialTheme.typography.bodySmall)
                    }
                    ConnectionStatus.WAITING_FOR_NETWORK -> TonePill(
                        text = stringResource(R.string.tg_waiting_network),
                        container = scheme.errorContainer,
                        content = scheme.onErrorContainer
                    )
                    ConnectionStatus.CONNECTED -> Unit
                }
            }
            TgHeader(Icons.Rounded.Link, stringResource(R.string.tg_resolver_body))
            OutlinedTextField(
                value = input,
                onValueChange = vm::setInput,
                label = { Text(stringResource(R.string.tg_link_label)) },
                placeholder = { Text(stringResource(R.string.tg_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = FieldShape,
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                isError = ui is ResolveUi.Error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.resolve() }),
                trailingIcon = {
                    FilledTonalIconButton(onClick = { clipboard.getText()?.text?.let(vm::setInput) }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.tg_paste))
                    }
                }
            )
            PrimaryButton(
                text = stringResource(if (ui == ResolveUi.Loading) R.string.tg_resolving else R.string.tg_resolve),
                onClick = vm::resolve,
                loading = ui == ResolveUi.Loading,
                icon = Icons.Rounded.Search
            )
            (ui as? ResolveUi.Error)?.let { ErrorCard(it.error) }
        }
    }
}

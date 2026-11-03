package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthState
import com.dparadox.paradoxuploader.ui.util.UiActions

@Composable
fun LoginScreen(vm: TelegramAuthViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by rememberSaveable { mutableStateOf("") }
    val needsApi = auth is AuthState.NeedsApiCredentials

    TgScaffold(stringResource(R.string.tg_login_title)) { padding ->
        TgForm(padding) {
            Text(stringResource(R.string.tg_login_body), style = MaterialTheme.typography.bodyMedium)

            if (auth is AuthState.Unsupported) {
                Text(
                    stringResource(R.string.tg_unsupported_step, (auth as AuthState.Unsupported).step),
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (needsApi) {
                Text(stringResource(R.string.tg_api_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.tg_api_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = apiId, onValueChange = { apiId = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tg_api_id_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = apiHash, onValueChange = { apiHash = it.trim() },
                    label = { Text(stringResource(R.string.tg_api_hash_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.saveApi(apiId, apiHash) }, enabled = !ui.busy) {
                        Text(stringResource(R.string.tg_api_save))
                    }
                    OutlinedButton(onClick = { UiActions.open(context, "https://my.telegram.org/apps") }) {
                        Text(stringResource(R.string.tg_api_open_site))
                    }
                }
            } else {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                    label = { Text(stringResource(R.string.tg_phone_label)) },
                    placeholder = { Text(stringResource(R.string.tg_phone_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!ui.busy) vm.phone(phone) })
                )
                Button(
                    onClick = { vm.phone(phone) },
                    enabled = !ui.busy && auth is AuthState.WaitPhone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (ui.busy || auth is AuthState.Initializing) InlineSpinner() else Text(stringResource(R.string.tg_continue))
                }
            }
            ui.error?.let { ErrorCard(it) }
        }
    }
}

@Composable
fun OtpScreen(vm: TelegramAuthViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val phone = (auth as? AuthState.WaitCode)?.phone.orEmpty()

    TgScaffold(stringResource(R.string.tg_otp_title)) { padding ->
        TgForm(padding) {
            Text(stringResource(R.string.tg_otp_body, phone), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter(Char::isDigit).take(8)
                    if (code.length == 5 && !ui.busy) vm.code(code)
                },
                label = { Text(stringResource(R.string.tg_otp_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!ui.busy) vm.code(code) })
            )
            Button(onClick = { vm.code(code) }, enabled = !ui.busy && code.length >= 4, modifier = Modifier.fillMaxWidth()) {
                if (ui.busy) InlineSpinner() else Text(stringResource(R.string.tg_verify))
            }
            TextButton(onClick = vm::useDifferentNumber, enabled = !ui.busy) {
                Text(stringResource(R.string.tg_use_other_number))
            }
            ui.error?.let { ErrorCard(it) }
        }
    }
}

@Composable
fun TwoFactorScreen(vm: TelegramAuthViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val hint = (auth as? AuthState.WaitPassword)?.hint.orEmpty()

    TgScaffold(stringResource(R.string.tg_2fa_title)) { padding ->
        TgForm(padding) {
            Text(stringResource(R.string.tg_2fa_body), style = MaterialTheme.typography.bodyMedium)
            if (hint.isNotBlank()) {
                Text(stringResource(R.string.tg_2fa_hint, hint), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.tg_password_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!ui.busy) vm.password(password) }),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (visible) R.string.tg_hide_password else R.string.tg_show_password)
                        )
                    }
                }
            )
            Button(onClick = { vm.password(password) }, enabled = !ui.busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                if (ui.busy) InlineSpinner() else Text(stringResource(R.string.tg_verify))
            }
            ui.error?.let { ErrorCard(it) }
        }
    }
}

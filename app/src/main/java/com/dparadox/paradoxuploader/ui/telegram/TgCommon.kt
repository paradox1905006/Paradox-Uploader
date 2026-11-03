@file:OptIn(ExperimentalMaterial3Api::class)

package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthFailure
import com.dparadox.paradoxuploader.domain.telegram.FileKind
import com.dparadox.paradoxuploader.domain.telegram.TgError

@Composable
fun tgErrorText(error: TgError): String = when (error) {
    TgError.EmptyLink -> stringResource(R.string.tg_err_empty)
    TgError.InvalidLink -> stringResource(R.string.tg_err_invalid)
    TgError.InviteLink -> stringResource(R.string.tg_err_invite)
    TgError.MessageNotFound -> stringResource(R.string.tg_err_message_not_found)
    TgError.ChatNotAccessible -> stringResource(R.string.tg_err_chat_inaccessible)
    TgError.PrivateChannelRestricted -> stringResource(R.string.tg_err_private_channel)
    TgError.PermissionDenied -> stringResource(R.string.tg_err_permission)
    TgError.ProtectedContent -> stringResource(R.string.tg_err_protected)
    TgError.UnsupportedMedia -> stringResource(R.string.tg_err_unsupported_media)
    TgError.NotLoggedIn -> stringResource(R.string.tg_err_not_logged_in)
    TgError.ApiCredentialsMissing -> stringResource(R.string.tg_err_api_missing)
    TgError.SessionExpired -> stringResource(R.string.tg_err_session_expired)
    TgError.Network -> stringResource(R.string.tg_err_network)
    TgError.RateLimited -> stringResource(R.string.tg_err_rate_limited)
    TgError.LinkGenerationFailed -> stringResource(R.string.tg_err_link_failed)
    is TgError.FloodWait -> stringResource(R.string.tg_err_flood, error.seconds)
    is TgError.AuthFailed -> stringResource(
        when (error.reason) {
            AuthFailure.PHONE_INVALID -> R.string.tg_err_phone
            AuthFailure.CODE_INVALID -> R.string.tg_err_code
            AuthFailure.CODE_EXPIRED -> R.string.tg_err_code_expired
            AuthFailure.PASSWORD_INVALID -> R.string.tg_err_password
            AuthFailure.OTHER -> R.string.tg_err_auth_other
        }
    )
    is TgError.Mtproto -> stringResource(R.string.tg_err_mtproto, error.code, error.message)
    is TgError.Unknown -> stringResource(R.string.tg_err_unknown, error.message.orEmpty())
}

@Composable
fun kindLabel(kind: FileKind): String = stringResource(
    when (kind) {
        FileKind.DOCUMENT -> R.string.kind_document
        FileKind.VIDEO -> R.string.kind_video
        FileKind.AUDIO -> R.string.kind_audio
        FileKind.IMAGE -> R.string.kind_image
        FileKind.APK -> R.string.kind_apk
        FileKind.ARCHIVE -> R.string.kind_archive
        FileKind.VOICE -> R.string.kind_voice
        FileKind.ANIMATION -> R.string.kind_animation
        FileKind.OTHER -> R.string.kind_other
    }
)

@Composable
fun TgScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.tg_back))
                        }
                    }
                },
                actions = { actions() }
            )
        },
        content = content
    )
}

/** Scrollable, width-limited column so forms look right on phones and tablets alike. */
@Composable
fun TgForm(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { content() }
    }
}

@Composable
fun ErrorCard(error: TgError, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(tgErrorText(error), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun CenteredLoading(text: String? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        if (text != null) Text(text, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InlineSpinner() = CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)

fun formatRemaining(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

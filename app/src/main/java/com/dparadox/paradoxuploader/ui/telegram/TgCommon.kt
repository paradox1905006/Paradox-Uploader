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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.telegram.AuthFailure
import com.dparadox.paradoxuploader.domain.telegram.FileKind
import com.dparadox.paradoxuploader.domain.telegram.TgError
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.LogoMark
import com.dparadox.paradoxuploader.ui.components.ParadoxScaffold
import com.dparadox.paradoxuploader.ui.components.Spacing

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
    ParadoxScaffold(
        title = title,
        onBack = onBack,
        actions = { actions() },
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
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) { content() }
    }
}

/** Big icon + headline + supporting text shown at the top of Telegram forms. */
@Composable
fun TgHeader(icon: ImageVector, body: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon,
            size = 56.dp,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ErrorCard(error: TgError, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                Icons.Rounded.ErrorOutline,
                size = 36.dp,
                container = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f),
                content = MaterialTheme.colorScheme.onErrorContainer,
                circle = true
            )
            Text(tgErrorText(error), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CenteredLoading(text: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            LogoMark(size = 40.dp)
        }
        if (text != null) {
            Text(
                text,
                modifier = Modifier.padding(top = Spacing.xl),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InlineSpinner() = CircularProgressIndicator(
    Modifier.size(18.dp),
    strokeWidth = 2.dp,
    strokeCap = StrokeCap.Round,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

fun formatRemaining(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

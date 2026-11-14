package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.ui.components.BannerTone
import com.dparadox.paradoxuploader.ui.components.HeroCard
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InfoBanner
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.TonePill
import com.dparadox.paradoxuploader.ui.util.UiActions

@Composable
fun DownloadLinkScreen(
    onBack: () -> Unit,
    vm: FileActionsViewModel = hiltViewModel()
) {
    val file by vm.file.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val linkUi by vm.linkUi.collectAsStateWithLifecycle()
    val remaining by vm.remainingMs.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.ensureLink() }

    TgScaffold(stringResource(R.string.tg_link_title), onBack = onBack) { padding ->
        val current = link
        when {
            linkUi is LinkUi.Creating && current == null -> CenteredLoading(stringResource(R.string.tg_creating_link))
            current != null -> TgForm(padding) {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IconTile(
                            Icons.Rounded.Link,
                            size = 48.dp,
                            container = MaterialTheme.colorScheme.primary,
                            content = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            stringResource(R.string.tg_link_url),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                current.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }
                    val ms = remaining
                    val expired = ms != null && ms <= 0L
                    TonePill(
                        text = if (expired) stringResource(R.string.tg_link_expired)
                        else stringResource(R.string.tg_link_expires_in, formatRemaining(ms ?: (current.expiresAt - System.currentTimeMillis()))),
                        container = if (expired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        content = if (expired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { UiActions.copy(context, current.url) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.tg_copy_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                    FilledTonalButton(onClick = { UiActions.share(context, current.url) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.tg_share_link), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { UiActions.open(context, current.url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_open_browser), modifier = Modifier.padding(start = 8.dp))
                }
                InfoBanner(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = stringResource(R.string.tg_link_local_note),
                    tone = BannerTone.NEUTRAL
                )
                TextButton(
                    onClick = { vm.revoke(); onBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tg_revoke), modifier = Modifier.padding(start = 8.dp))
                }
            }
            else -> TgForm(padding) {
                // Fallback: URL generation failed -> use the authenticated session directly.
                InfoBanner(
                    icon = Icons.Rounded.LinkOff,
                    title = stringResource(R.string.tg_link_failed_title),
                    body = stringResource(R.string.tg_link_failed_body),
                    tone = BannerTone.ERROR
                )
                (linkUi as? LinkUi.Failed)?.let { ErrorCard(it.error) }
                PrimaryButton(text = stringResource(R.string.tg_get_link), onClick = { vm.ensureLink() }, icon = Icons.Rounded.Refresh)
                file?.let { f ->
                    DownloadSection(f, download, onStart = vm::startDownload, onCancel = vm::cancelDownload)
                }
            }
        }
    }
}

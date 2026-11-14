package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.data.telegram.HomeTab
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination
import com.dparadox.paradoxuploader.ui.components.EmptyState
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.InitialAvatar
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxProgress
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.components.SearchPill
import com.dparadox.paradoxuploader.ui.components.Spacing
import com.dparadox.paradoxuploader.ui.components.TonePill

/** Telegram tab: two tabs, Upload (with its default channel) and Download (browse files of its default channel). */
@Composable
fun TelegramHomeScreen(
    onOpenFiles: (Long) -> Unit,
    onCreate: () -> Unit,
    onPasteLink: () -> Unit,
    home: TelegramHomeViewModel = hiltViewModel()
) {
    val tab by home.tab.collectAsStateWithLifecycle()

    TgScaffold(
        title = stringResource(R.string.nav_telegram),
        actions = {
            FilledTonalButton(
                onClick = onPasteLink,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.padding(end = Spacing.sm)
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.tg_paste_link_action))
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            ) {
                val tabs = listOf(
                    Triple(HomeTab.UPLOAD, R.string.tg_tab_upload, Icons.Rounded.CloudUpload),
                    Triple(HomeTab.DOWNLOAD, R.string.tg_tab_download, Icons.Rounded.CloudDownload)
                )
                tabs.forEachIndexed { index, (value, label, icon) ->
                    SegmentedButton(
                        selected = tab == value,
                        onClick = { home.selectTab(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = tab == value) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        }
                    ) { Text(stringResource(label)) }
                }
            }
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (tab) {
                    HomeTab.UPLOAD -> UploadPane(onCreate = onCreate)
                    HomeTab.DOWNLOAD -> DownloadPane(onOpenFiles = onOpenFiles, onCreate = onCreate)
                }
            }
        }
    }
}

@Composable
fun DownloadPane(
    onOpenFiles: (Long) -> Unit,
    onCreate: () -> Unit,
    home: TelegramHomeViewModel = hiltViewModel()
) {
    val current by home.downloadDefault.collectAsStateWithLifecycle()
    val pins by home.downloadPins.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            DestinationSection(HomeTab.DOWNLOAD, current, pins, onCreate, home)
        }
        item {
            val selected = current
            if (selected != null) {
                PrimaryButton(
                    text = "Browse files in ${selected.title}",
                    onClick = { onOpenFiles(selected.chatId) },
                    icon = Icons.Rounded.FolderOpen
                )
            } else {
                EmptyState(
                    icon = Icons.Rounded.CloudDownload,
                    title = "No channel selected",
                    body = "Pick a default channel above, then browse its files here."
                )
            }
        }
    }
}

/** Shows the tab's default channel, its pinned channels, and a search box to find and pin more. */
@Composable
fun DestinationSection(
    tab: HomeTab,
    current: ChatDestination?,
    pins: List<ChatDestination>,
    onCreate: () -> Unit,
    home: TelegramHomeViewModel
) {
    val search by home.search.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    ParadoxCard(spacing = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (current == null) {
                IconTile(
                    Icons.AutoMirrored.Rounded.Send,
                    size = 48.dp,
                    container = scheme.surfaceContainerHighest,
                    content = scheme.onSurfaceVariant,
                    circle = true
                )
            } else {
                InitialAvatar(current.title, size = 48.dp, container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (current == null) "No default channel yet" else current.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (current != null) {
                    TonePill(text = "Default", container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
                } else {
                    Text(
                        "Search below and set one as default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }

        if (pins.isNotEmpty()) {
            Text("Pinned", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(pins, key = { it.chatId }) { p ->
                    val selected = current?.chatId == p.chatId
                    FilterChip(
                        selected = selected,
                        onClick = { home.setDefault(tab, p) },
                        label = { Text(p.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(
                                if (selected) Icons.Rounded.Star else Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        shape = CircleShape
                    )
                }
            }
        }

        SearchPill(
            value = search.query,
            onValueChange = { home.query(it) },
            placeholder = "Search channels & groups"
        )
        if (search.busy) ParadoxProgress(progress = null)
        search.error?.let { ErrorCard(it) }

        if (search.query.isNotBlank() && !search.busy && search.results.isEmpty() && search.error == null) {
            Text(
                "Nothing found. Try another name, or create a channel.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }

        search.results.forEachIndexed { index, d ->
            if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            val isPinned = pins.any { it.chatId == d.chatId }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(d.title, size = 40.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(d.typeLabel, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = { home.togglePin(tab, d) }) {
                    Icon(
                        if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) scheme.primary else scheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { home.setDefault(tab, d) }) { Text("Set default") }
            }
        }

        TextButton(onClick = onCreate) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Create new channel")
        }
    }
}

package com.dparadox.paradoxuploader.ui.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dparadox.paradoxuploader.data.telegram.HomeTab
import com.dparadox.paradoxuploader.domain.telegram.ChatDestination

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
        title = "Telegram",
        actions = { TextButton(onClick = onPasteLink) { Text("Paste link") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == HomeTab.UPLOAD,
                    onClick = { home.selectTab(HomeTab.UPLOAD) },
                    text = { Text("Upload") }
                )
                Tab(
                    selected = tab == HomeTab.DOWNLOAD,
                    onClick = { home.selectTab(HomeTab.DOWNLOAD) },
                    text = { Text("Download") }
                )
            }
            when (tab) {
                HomeTab.UPLOAD -> UploadPane(onCreate = onCreate)
                HomeTab.DOWNLOAD -> DownloadPane(onOpenFiles = onOpenFiles, onCreate = onCreate)
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DestinationSection(HomeTab.DOWNLOAD, current, pins, onCreate, home)
        }
        item {
            if (current != null) {
                Button(onClick = { onOpenFiles(current!!.chatId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Browse files in ${current!!.title}")
                }
            } else {
                Text(
                    "Pick a default channel above, then browse its files here.",
                    style = MaterialTheme.typography.bodyMedium
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (current == null) "No default channel yet" else "Default: ${current.title}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (pins.isNotEmpty()) {
                Text("Pinned", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pins, key = { it.chatId }) { p ->
                        FilterChip(
                            selected = current?.chatId == p.chatId,
                            onClick = { home.setDefault(tab, p) },
                            label = { Text(p.title.ifBlank { "Untitled" }) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = search.query,
                onValueChange = { home.query(it) },
                label = { Text("Search channels & groups") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (search.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            search.error?.let { ErrorCard(it) }

            if (search.query.isNotBlank() && !search.busy && search.results.isEmpty() && search.error == null) {
                Text("Nothing found. Try another name, or create a channel.", style = MaterialTheme.typography.bodySmall)
            }

            search.results.forEach { d ->
                val isPinned = pins.any { it.chatId == d.chatId }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge)
                        Text(d.typeLabel, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { home.togglePin(tab, d) }) { Text(if (isPinned) "Unpin" else "Pin") }
                    TextButton(onClick = { home.setDefault(tab, d) }) { Text("Set default") }
                }
            }

            TextButton(onClick = onCreate) { Text("Create new channel") }
        }
    }
}

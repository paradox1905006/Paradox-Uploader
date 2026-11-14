package com.dparadox.paradoxuploader.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.ParadoxCard
import com.dparadox.paradoxuploader.ui.components.ParadoxScaffold
import com.dparadox.paradoxuploader.ui.components.ScreenColumn
import com.dparadox.paradoxuploader.ui.components.Spacing

/**
 * Help, terms and conditions. The wording describes how the app works today.
 * Have the legal sections reviewed before publishing a release.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    ParadoxScaffold(title = "Help & terms", onBack = onBack) { padding ->
        ScreenColumn(padding, spacing = Spacing.md) {
            val scheme = MaterialTheme.colorScheme
            Block(Icons.Rounded.Lightbulb, "How to use Paradox Uploader", HOW_TO_USE, scheme.primaryContainer, scheme.onPrimaryContainer)
            Block(Icons.Rounded.CloudUpload, "Archive.org upload", ARCHIVE_HELP, scheme.primaryContainer, scheme.onPrimaryContainer)
            Block(Icons.AutoMirrored.Rounded.Send, "Telegram upload", TELEGRAM_HELP, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            Block(Icons.Rounded.Settings, "Settings", SETTINGS_HELP, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Block(Icons.Rounded.Shield, "Privacy", PRIVACY, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            Block(Icons.Rounded.Gavel, "Terms of use", TERMS, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Block(Icons.Rounded.Rule, "Conditions", CONDITIONS, scheme.secondaryContainer, scheme.onSecondaryContainer)
        }
    }
}

private val STEP = Regex("^(\\d+)\\.\\s+(.*)$")

@Composable
private fun Block(icon: ImageVector, title: String, body: List<String>, container: Color, content: Color) {
    ParadoxCard(spacing = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, container = container, content = content)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Spacing.md)
            )
        }
        body.forEach { line ->
            val step = STEP.find(line)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                if (step != null) {
                    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(step.groupValues[1], style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Box(Modifier.padding(top = 7.dp).size(24.dp), contentAlignment = Alignment.TopCenter) {
                        Surface(shape = CircleShape, color = content.copy(alpha = 0.5f), modifier = Modifier.size(6.dp)) {}
                    }
                }
                Text(
                    step?.groupValues?.get(2) ?: line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val HOW_TO_USE = listOf(
    "Paradox Uploader sends files to two places: Internet Archive (archive.org) and Telegram. Pick the one you need from the bottom bar.",
    "Your files stay on your phone until you upload them. The app does not run a server that stores your files.",
    "Before the first upload, add your own keys in Settings. Nothing works without them."
)

private val ARCHIVE_HELP = listOf(
    "1. Open Settings and enter your archive.org S3 access key and secret. They are stored encrypted on this device.",
    "2. Open the Upload tab, choose one or more files, and optionally add a title, description, tags, or a folder.",
    "3. Tap Upload. Uploads run in the background and show progress in the notification.",
    "4. When a file is ready, its link appears in the Files tab. Duplicate files are flagged and are not sent again.",
    "5. If an upload fails, use Retry. If it was interrupted, the app checks archive.org first, so a file already saved is not uploaded twice."
)

private val TELEGRAM_HELP = listOf(
    "1. Open the Telegram tab and sign in with your own Telegram account (phone number, login code, and 2-step password if you use one). Get your API id and hash from my.telegram.org.",
    "2. Create a cloud channel, or pick an existing channel or group you already manage. Your account must be allowed to post there.",
    "3. Upload: choose the destination, optionally a forum topic, pick files, write a caption, and tap Upload. Uploads continue when you leave the app.",
    "4. Each finished upload shows its message link. You can copy, share, or open it in Telegram.",
    "5. Cloud channels: the Files screen lists the channel's whole history so you can select and download files. Links you create are temporary and work only while this app is running on your phone.",
    "6. Forum topics: loading and creating topics is not available in this version yet. Uploads go to the General topic."
)

private val SETTINGS_HELP = listOf(
    "Keys: your archive.org keys and the Telegram API credentials.",
    "Upload: default collection, media type, Wi-Fi only, charging only, and how many uploads run at once.",
    "History and cleanup: sync your archive.org history and remove empty folders.",
    "Appearance, security (app lock), background behaviour, and update checks.",
    "About: app version, terms page, and this help page."
)

private val PRIVACY = listOf(
    "Your archive.org keys and Telegram session are stored encrypted on your device. Paradox Uploader does not send them to any server of its own.",
    "Files are sent directly from your phone to archive.org or Telegram. They do not pass through a Paradox Uploader server.",
    "When update checks are on, the app contacts the update server to see if a new version exists. Turn this off in Settings if you do not want that.",
    "Telegram and Internet Archive handle their own data under their own privacy policies."
)

private val TERMS = listOf(
    "You are responsible for everything you upload, including its content and its legality.",
    "Only upload files you have the right to share. Do not upload copyrighted material you do not own or have permission to distribute, or content that is illegal where you live.",
    "Use of archive.org is also subject to the Internet Archive's terms. Use of Telegram is subject to Telegram's terms. Follow both.",
    "Do not use the app to harass people, spam, or break the rules of a channel or group.",
    "The app is provided as is, without warranty. The developer is not liable for lost data, failed uploads, or account actions taken by archive.org or Telegram."
)

private val CONDITIONS = listOf(
    "Keep your keys and Telegram login private. Anyone with them can use your accounts.",
    "Links created by this app may be public. Think before sharing a link.",
    "The developer may change or stop features at any time. Important changes are described in release notes.",
    "By using Paradox Uploader you agree to these terms and conditions."
)

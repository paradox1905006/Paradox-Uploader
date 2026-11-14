package com.dparadox.paradoxuploader.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.util.ArchiveUrls
import com.dparadox.paradoxuploader.ui.components.IconTile
import com.dparadox.paradoxuploader.ui.components.LogoMark
import com.dparadox.paradoxuploader.ui.components.PrimaryButton
import com.dparadox.paradoxuploader.ui.util.UiActions
import kotlinx.coroutines.launch

private data class OnboardingPage(val icon: ImageVector?, val title: Int, val body: Int)

/** Large layered illustration: soft gradient blob, ring and the page icon (or the logo). */
@Composable
private fun Illustration(icon: ImageVector?, container: Color, content: Color) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(container.copy(alpha = 0.9f), container.copy(alpha = 0f))))
        )
        Box(
            Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(container)
        )
        if (icon == null) {
            LogoMark(size = 120.dp, container = scheme.primary, tint = scheme.onPrimary)
        } else {
            IconTile(icon, size = 112.dp, container = content, content = container, circle = true)
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val pages = listOf(
        OnboardingPage(null, R.string.onboarding_1_title, R.string.onboarding_1_body),
        OnboardingPage(Icons.Rounded.CloudUpload, R.string.onboarding_2_title, R.string.onboarding_2_body),
        OnboardingPage(Icons.Rounded.Key, R.string.onboarding_3_title, R.string.onboarding_3_body)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerLow, scheme.surface)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(size = 36.dp)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp).weight(1f)
            )
            if (!isLast) TextButton(onClick = onFinished) { Text(stringResource(R.string.skip)) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val (container, content) = when (index) {
                0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Illustration(page.icon, container, content)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(page.body),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = scheme.onSurfaceVariant
                    )
                    if (index == pages.lastIndex) {
                        OutlinedButton(onClick = { UiActions.open(context, ArchiveUrls.KEYS_PAGE) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_keys_page))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages.size) { index ->
                    val active = index == pagerState.currentPage
                    val width by animateDpAsState(if (active) 28.dp else 8.dp, label = "dot-width")
                    val color by animateColorAsState(
                        if (active) scheme.primary else scheme.outlineVariant,
                        label = "dot-color"
                    )
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            Button(
                onClick = {
                    if (isLast) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Text(
                    stringResource(if (isLast) R.string.get_started else R.string.next),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Explains why notifications are needed before the system permission dialog appears (Android 13+). */
@Composable
fun NotificationRationaleScreen(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerLow, scheme.surface)))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Illustration(Icons.Rounded.Notifications, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rationale_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.rationale_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            PrimaryButton(
                text = stringResource(R.string.allow_notifications),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onDone()
                    }
                },
                icon = Icons.Rounded.Notifications
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.not_now)) }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.dparadox.paradoxuploader.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.R

/*
 * Shared building blocks of the Paradox design system. Every screen is composed from these so
 * spacing, corner radii, colour roles and typography stay identical across the app.
 */

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val screen = 16.dp
}

/** Max content width so lists and forms look right on tablets and foldables too. */
val ContentMaxWidth = 720.dp

// ------------------------------------------------------------------ Scaffold & app bars

/**
 * Screen scaffold with a Material 3 large, collapsing top app bar. The title starts big and
 * collapses into the bar while the content scrolls, like Google's own apps.
 */
@Composable
fun ParadoxScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) BackButton(onBack) else navigationIcon()
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = contentWindowInsets,
        content = content
    )
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.tg_back))
    }
}

/** Centers content and limits its width; used by every scrolling screen body. */
@Composable
fun CenteredContent(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) { content() }
    }
}

/** Scrollable column body with the standard screen padding and spacing. */
@Composable
fun ScreenColumn(
    padding: PaddingValues,
    spacing: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    CenteredContent(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.sm, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

// ------------------------------------------------------------------ Surfaces

/** The standard tonal card: large rounded corners, no shadow, tinted container. */
@Composable
fun ParadoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    spacing: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = color,
            contentColor = contentColor,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = color,
            contentColor = contentColor,
            content = body
        )
    }
}

/** Expressive gradient card used for the main call to action of a screen. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)
                )
            )
    ) {
        // Soft decorative orbs for depth.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 0.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(scheme.surface.copy(alpha = 0.16f))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(96.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.08f))
        )
        Column(
            Modifier.padding(Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content
        )
    }
}

/** Section title shown above a group of cards. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) Row(verticalAlignment = Alignment.CenterVertically, content = action)
    }
}

/** Small uppercase-free group label in the primary colour, as in system Settings. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.xs, top = Spacing.sm)
    )
}

// ------------------------------------------------------------------ Icons & avatars

/** Rounded-square tonal container with an icon inside. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    circle: Boolean = false
) {
    Box(
        modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(size * 0.34f))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.52f))
    }
}

/** Circle with the first letter of [name]; used for Telegram chats and accounts. */
@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            color = content,
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        )
    }
}

/** The Paradox logo on a tonal tile. */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(container),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_paradox),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

// ------------------------------------------------------------------ Buttons & progress

/** Tall, full-width primary call to action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = Spacing.xxl, vertical = Spacing.md)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.md))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Thick, rounded linear progress. Pass null for an indeterminate bar. */
@Composable
fun ParadoxProgress(
    progress: (() -> Float)?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val m = modifier.fillMaxWidth().height(6.dp)
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress,
            modifier = m,
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    } else {
        LinearProgressIndicator(
            modifier = m,
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    }
}

// ------------------------------------------------------------------ Chips & pills

/** Small tonal pill with an optional leading dot. */
@Composable
fun TonePill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = true
) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(start = if (dot) 8.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dot) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(content))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ Banners & states

enum class BannerTone { INFO, ACCENT, NEUTRAL, ERROR }

@Composable
fun InfoBanner(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    tone: BannerTone = BannerTone.INFO,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BannerTone.INFO -> scheme.primaryContainer to scheme.onPrimaryContainer
        BannerTone.ACCENT -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        BannerTone.NEUTRAL -> scheme.secondaryContainer to scheme.onSecondaryContainer
        BannerTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                IconTile(icon, size = 36.dp, container = content.copy(alpha = 0.12f), content = content, circle = true)
                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
                }
            }
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = actions
                )
            }
        }
    }
}

/** Friendly empty / error state with a large tonal icon. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            )
            IconTile(
                icon,
                size = 80.dp,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                circle = true
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.xs))
            action()
        }
    }
}

/** Pulsing placeholder blocks shown while a list loads. */
@Composable
fun SkeletonBlocks(count: Int = 6, height: Dp = 88.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    Column(modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                )
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        Modifier.fillMaxWidth(0.7f).height(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                    )
                    Box(
                        Modifier.fillMaxWidth(0.45f).height(10.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Rows

/** A list row with a leading icon tile, a title, an optional subtitle and a trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(clickable)
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        when {
            leading != null -> leading()
            icon != null -> IconTile(
                icon,
                size = 40.dp,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                circle = true
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** A [ListRow] with a switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        onClick = { onChecked(!checked) }
    ) {
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

/** Label above a value, with an optional trailing action (for example a copy button). */
@Composable
fun DetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (trailing != null) trailing()
    }
}

// ------------------------------------------------------------------ Inputs

/** Pill-shaped filled search field. */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearDescription: String? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = clearDescription)
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    )
}

/** Corner shape used by every outlined text field in the app. */
val FieldShape @Composable get() = MaterialTheme.shapes.medium

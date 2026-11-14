package com.dparadox.paradoxuploader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.AppSettings
import com.dparadox.paradoxuploader.domain.model.ThemeMode

/*
 * Paradox design system.
 *
 * Material 3 / Material You: the wallpaper-based dynamic palette is used on Android 12+ when
 * "Dynamic color" is on. Otherwise the brand palette below (cobalt blue primary, slate secondary,
 * teal tertiary used for "ready / success") is used. All screens read colours, shapes and type
 * from MaterialTheme only, so both palettes and AMOLED mode work everywhere.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0053DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF003EA8),
    inversePrimary = Color(0xFFB4C5FF),
    secondary = Color(0xFF595E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1F9),
    onSecondaryContainer = Color(0xFF414659),
    tertiary = Color(0xFF086B5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA0F2DD),
    onTertiaryContainer = Color(0xFF005144),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF0053DB),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757680),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDAD9E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF003EA8),
    onPrimaryContainer = Color(0xFFDBE1FF),
    inversePrimary = Color(0xFF0053DB),
    secondary = Color(0xFFC1C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF414659),
    onSecondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFF85D6C1),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFFA0F2DD),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB4C5FF),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceDim = Color(0xFF121318)
)

/** Rounded, friendly corner scale used across cards, sheets, chips and fields. */
private val ParadoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Plus Jakarta Sans (variable, SIL Open Font License), bundled in res/font. */
val ParadoxFontFamily = FontFamily(
    jakarta(FontWeight.Light),
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold)
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = ParadoxFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em
)

private val ParadoxTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.02),
    displayMedium = style(45, 52, FontWeight.Bold, -0.02),
    displaySmall = style(36, 44, FontWeight.Bold, -0.015),
    headlineLarge = style(32, 40, FontWeight.Bold, -0.015),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.01),
    headlineSmall = style(24, 32, FontWeight.Bold, -0.005),
    titleLarge = style(22, 28, FontWeight.Bold, -0.005),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.0),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.0),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.0),
    bodySmall = style(12, 16, FontWeight.Normal, 0.005),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.005),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.02),
    labelSmall = style(11, 16, FontWeight.SemiBold, 0.03)
)

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E23)
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ParadoxTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && settings.amoled) scheme = scheme.toAmoled()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = ParadoxShapes,
        typography = ParadoxTypography,
        content = content
    )
}

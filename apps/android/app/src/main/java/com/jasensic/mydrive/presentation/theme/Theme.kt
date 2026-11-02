package com.jasensic.mydrive.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jasensic.mydrive.domain.ThemeMode

// Mapping used across the app:
//   background        grouped background (the page)
//   surface           rows and cards sitting on the page
//   surfaceVariant    fills: search field, segmented control, chips
//   outlineVariant    hairline separators
//   onSurfaceVariant  secondary label
private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentLight.copy(alpha = 0.12f),
    onPrimaryContainer = AccentLight,
    secondary = AccentLight,
    onSecondary = Color.White,
    secondaryContainer = FillLight,
    onSecondaryContainer = LabelLight,
    tertiary = AccentLight,
    background = GroupedBackgroundLight,
    onBackground = LabelLight,
    surface = SurfaceLight,
    onSurface = LabelLight,
    surfaceVariant = FillLight,
    onSurfaceVariant = SecondaryLabelLight,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = FillLight,
    outline = SecondaryLabelLight,
    outlineVariant = SeparatorLight,
    error = DestructiveLight,
    onError = Color.White,
    errorContainer = DestructiveLight.copy(alpha = 0.12f),
    onErrorContainer = DestructiveLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = AccentDark.copy(alpha = 0.20f),
    onPrimaryContainer = AccentDark,
    secondary = AccentDark,
    onSecondary = Color.White,
    secondaryContainer = FillDark,
    onSecondaryContainer = LabelDark,
    tertiary = AccentDark,
    background = GroupedBackgroundDark,
    onBackground = LabelDark,
    surface = SurfaceDark,
    onSurface = LabelDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = SecondaryLabelDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = GroupedBackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = FillDark,
    outline = SecondaryLabelDark,
    outlineVariant = SeparatorDark,
    error = DestructiveDark,
    onError = Color.White,
    errorContainer = DestructiveDark.copy(alpha = 0.20f),
    onErrorContainer = DestructiveDark,
)

/** Soft corners everywhere; dialogs use the iOS alert radius. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun MyDriveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

package com.jasensic.mydrive.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jasensic.mydrive.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Amber,
    onSecondary = Color(0xFF3B2500),
    secondaryContainer = Color(0xFFFFE7C2),
    onSecondaryContainer = Color(0xFF3B2500),
    tertiary = Color(0xFF6366F1),
    background = Mist,
    onBackground = Color(0xFF10201C),
    surface = Paper,
    onSurface = Color(0xFF10201C),
    surfaceVariant = Cloud,
    onSurfaceVariant = Color(0xFF3F4F4B),
    outline = Color(0xFF6B7C77),
    error = DangerLight,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = AmberDark,
    onSecondary = Color(0xFF3B2500),
    secondaryContainer = Color(0xFF7A4B05),
    onSecondaryContainer = Color(0xFFFFE7C2),
    tertiary = Color(0xFFA5B4FC),
    background = Ink,
    onBackground = Color(0xFFE7F5F1),
    surface = Forest,
    onSurface = Color(0xFFE7F5F1),
    surfaceVariant = Slate,
    onSurfaceVariant = Color(0xFFB7C7C2),
    outline = Color(0xFF8AA09A),
    error = DangerDark,
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
        content = content,
    )
}

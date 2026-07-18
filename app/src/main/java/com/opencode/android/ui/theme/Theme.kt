package com.opencode.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OpenCodeColorScheme = darkColorScheme(
    primary = OpenCodePrimary,
    onPrimary = OpenCodeBackground,
    secondary = OpenCodeSecondary,
    onSecondary = OpenCodeBackground,
    background = OpenCodeBackground,
    onBackground = OpenCodeTextPrimary,
    surface = OpenCodeSurface,
    onSurface = OpenCodeTextPrimary,
    surfaceVariant = OpenCodeSurfaceVariant,
    onSurfaceVariant = OpenCodeTextSecondary,
    outline = OpenCodeOutline,
    error = OpenCodeError,
    onError = OpenCodeBackground
)

@Composable
fun OpenCodeAndroidTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = OpenCodeColorScheme.background.toArgb()
            window.navigationBarColor = OpenCodeColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = OpenCodeColorScheme,
        typography = Typography(),
        content = content
    )
}

// Mechanical compatibility while old screens are replaced in this branch.
@Composable
fun OpenCodeAssistantTheme(content: @Composable () -> Unit) = OpenCodeAndroidTheme(content)

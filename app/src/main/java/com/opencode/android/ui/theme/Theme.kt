package com.opencode.android.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OpenCodeColorScheme = darkColorScheme(
    primary = OpenCodeOrange,
    secondary = OpenCodePopYellow,
    tertiary = OpenCodeOrange,
    background = OpenCodeDarkGrey,
    surface = OpenCodeSurfaceGrey,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = OpenCodeTextPrimary,
    onSurface = OpenCodeTextPrimary,
    error = OpenCodeError
)

@Composable
fun OpenCodeAndroidTheme(
    content: @Composable () -> Unit
) {
    // Always use the OpenCode dark scheme
    val colorScheme = OpenCodeColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

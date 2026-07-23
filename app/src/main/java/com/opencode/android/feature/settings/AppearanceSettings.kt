package com.opencode.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opencode.android.ui.theme.ClaudeTheme
import com.opencode.android.ui.theme.DarkTheme
import com.opencode.android.ui.theme.GhosttyTheme
import com.opencode.android.ui.theme.LightTheme
import com.opencode.android.ui.theme.MidnightTheme
import com.opencode.android.ui.theme.ThemeColors
import com.opencode.android.ui.theme.ZincTheme

@Composable
fun ThemePickerDialog(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        "dark" to DarkTheme,
        "light" to LightTheme,
        "zinc" to ZincTheme,
        "midnight" to MidnightTheme,
        "claude" to ClaudeTheme,
        "ghostty" to GhosttyTheme,
        "auto" to DarkTheme
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                themes.forEach { (key, colors) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(key) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == key,
                            onClick = { onThemeChange(key) }
                        )
                        Text(
                            text = key.replaceFirstChar { it.uppercase() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                        ThemeColorPreview(colors)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThemeColorPreview(colors: ThemeColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(colors.surface0, colors.accent, colors.foreground).forEach { color ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

package com.opencode.android.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FocusModeOverlay(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        SmallFloatingActionButton(
            onClick = onToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = null
            )
        }
    }
}

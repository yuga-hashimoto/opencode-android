package com.opencode.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun RetainedPanel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (!visible) {
                    Modifier
                        .alpha(0f)
                        .pointerInput(Unit) {}
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

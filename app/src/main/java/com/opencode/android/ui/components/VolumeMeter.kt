package com.opencode.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun VolumeMeter(
    amplitude: Float,
    modifier: Modifier = Modifier,
    idle: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "volumePulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val barAmplitude = if (idle) {
                0.3f + 0.2f * abs(sin(phase + i * 1.2f))
            } else {
                (amplitude * (if (i == 1) 1f else 0.7f)).coerceIn(0.05f, 1f)
            }
            val height = 8.dp + barAmplitude * 16.dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

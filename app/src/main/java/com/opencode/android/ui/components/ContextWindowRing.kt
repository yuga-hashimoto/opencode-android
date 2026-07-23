package com.opencode.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.android.ui.theme.LocalThemeColors

@Composable
fun ContextWindowRing(
    usageFraction: Float,
    modifier: Modifier = Modifier,
    size: Int = 24
) {
    val tc = LocalThemeColors.current
    val clamped = usageFraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(600),
        label = "ring"
    )
    val color = when {
        clamped < 0.7f -> tc.success
        clamped < 0.9f -> tc.warning
        else -> tc.destructive
    }
    val trackColor = tc.border

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = 3.dp.toPx()
            val padding = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animated * 360f,
                    useCenter = false,
                    topLeft = Offset(padding, padding),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        if (size >= 28) {
            Text(
                text = "${(clamped * 100).toInt()}",
                fontSize = (size / 3.5).sp,
                color = color
            )
        }
    }
}

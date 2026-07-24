package com.opencode.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opencode.android.ui.theme.LocalThemeColors

enum class SessionStatus {
    RUNNING, WAITING, ERROR, PERMISSION, COMPLETED_UNREAD, IDLE
}

@Composable
fun StatusDot(
    status: SessionStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val tc = LocalThemeColors.current
    val targetColor = when (status) {
        SessionStatus.RUNNING -> tc.statusRunning
        SessionStatus.WAITING -> tc.statusWaiting
        SessionStatus.ERROR -> tc.statusError
        SessionStatus.PERMISSION -> tc.statusPermission
        SessionStatus.COMPLETED_UNREAD -> tc.statusWaiting
        SessionStatus.IDLE -> tc.statusIdle
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "statusDot"
    )
    Box(
        modifier = modifier
            .size(if (status == SessionStatus.COMPLETED_UNREAD) size + 2.dp else size)
            .clip(CircleShape)
            .then(
                if (status == SessionStatus.COMPLETED_UNREAD) {
                    Modifier.border(2.dp, color, CircleShape)
                } else {
                    Modifier
                }
            )
            .background(
                if (status == SessionStatus.COMPLETED_UNREAD) Color.Transparent else color
            )
    )
}

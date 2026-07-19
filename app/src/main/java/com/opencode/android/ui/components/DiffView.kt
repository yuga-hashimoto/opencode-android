package com.opencode.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencode.android.ui.theme.OpenCodeSuccess

private sealed interface DiffLine {
    data class Added(val text: String) : DiffLine
    data class Removed(val text: String) : DiffLine
    data class Hunk(val text: String) : DiffLine
    data class Context(val text: String) : DiffLine
}

private fun parseDiffLines(patch: String): List<DiffLine> =
    patch.lineSequence()
        .filterNot { it.startsWith("+++") || it.startsWith("---") }
        .map { line ->
            when {
                line.startsWith("@@") -> DiffLine.Hunk(line)
                line.startsWith("+") -> DiffLine.Added(line)
                line.startsWith("-") -> DiffLine.Removed(line)
                else -> DiffLine.Context(line)
            }
        }
        .toList()

/** Renders a unified-diff patch string with GitHub-style +/- line coloring. */
@Composable
fun DiffView(patch: String, modifier: Modifier = Modifier) {
    val lines = remember(patch) { parseDiffLines(patch) }
    val addedBackground = OpenCodeSuccess.copy(alpha = 0.16f)
    val removedBackground = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 6.dp)
                .width(IntrinsicSize.Max)
        ) {
            lines.forEach { line ->
                val background: Color
                val textColor: Color
                val fontWeight: FontWeight?
                val text: String
                when (line) {
                    is DiffLine.Added -> {
                        background = addedBackground
                        textColor = OpenCodeSuccess
                        fontWeight = null
                        text = line.text
                    }
                    is DiffLine.Removed -> {
                        background = removedBackground
                        textColor = MaterialTheme.colorScheme.error
                        fontWeight = null
                        text = line.text
                    }
                    is DiffLine.Hunk -> {
                        background = Color.Transparent
                        textColor = MaterialTheme.colorScheme.primary
                        fontWeight = FontWeight.SemiBold
                        text = line.text
                    }
                    is DiffLine.Context -> {
                        background = Color.Transparent
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        fontWeight = null
                        text = line.text
                    }
                }
                Text(
                    text = text,
                    color = textColor,
                    fontWeight = fontWeight,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background)
                        .padding(horizontal = 12.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** Compact "+N -M" summary line for a single file change, theme-adaptive. */
@Composable
fun DiffStatSummary(additions: Int, deletions: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        Text(
            text = "+$additions",
            color = OpenCodeSuccess,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "  -$deletions",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

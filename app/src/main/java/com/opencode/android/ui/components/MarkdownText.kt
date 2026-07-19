package com.opencode.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders chat-style Markdown (see [MarkdownParser]) using plain Compose Text/AnnotatedString —
 * no external Markdown dependency required for this narrow, chat-bubble use case.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val codeBackground = contentColor.copy(alpha = 0.12f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = joinLines(block.lines, contentColor, codeBackground),
                        color = contentColor
                    )
                }

                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = lineAnnotatedString(block.spans, contentColor, codeBackground),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = contentColor)
                                Text(lineAnnotatedString(item, contentColor, codeBackground), color = contentColor)
                            }
                        }
                    }
                }

                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${index + 1}.", color = contentColor)
                                Text(lineAnnotatedString(item, contentColor, codeBackground), color = contentColor)
                            }
                        }
                    }
                }

                is MarkdownBlock.CodeBlock -> CodeBlockView(block.language, block.code)

                MarkdownBlock.Divider -> HorizontalDivider(color = contentColor.copy(alpha = 0.24f))
            }
        }
    }
}

@Composable
private fun CodeBlockView(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.takeIf(String::isNotBlank) ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(top = 4.dp)
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private fun joinLines(
    lines: List<List<InlineSpan>>,
    contentColor: Color,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { index, spans ->
        if (index > 0) append("\n")
        append(lineAnnotatedString(spans, contentColor, codeBackground))
    }
}

private fun lineAnnotatedString(
    spans: List<InlineSpan>,
    contentColor: Color,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is InlineSpan.Plain -> append(span.text)
            is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is InlineSpan.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                    color = contentColor
                )
            ) { append(span.text) }
        }
    }
}

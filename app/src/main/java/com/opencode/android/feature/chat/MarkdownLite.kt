package com.opencode.android.feature.chat

sealed interface MarkdownInline {
    data class Plain(val text: String) : MarkdownInline
    data class Bold(val text: String) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String? = null) : MarkdownBlock
    data class BulletList(val items: List<List<MarkdownInline>>) : MarkdownBlock
}

object MarkdownLite {
    private val inlineRegex = Regex("`([^`]+)`|\\*\\*([^*]+)\\*\\*")

    fun parse(source: String): List<MarkdownBlock> {
        val lines = source.split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(parseInline(paragraphBuffer.joinToString("\n")))
                paragraphBuffer.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val language = trimmed.removePrefix("```").trim().ifBlank { null }
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines += lines[i]
                        i++
                    }
                    blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language)
                }
                trimmed.startsWith("#") -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    blocks += MarkdownBlock.Heading(level, parseInline(trimmed.drop(level).trim()))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    val items = mutableListOf<List<MarkdownInline>>()
                    while (i < lines.size && (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* "))) {
                        val itemLine = lines[i].trimStart()
                        items += parseInline(itemLine.drop(2).trim())
                        i++
                    }
                    blocks += MarkdownBlock.BulletList(items)
                    continue
                }
                trimmed.isBlank() -> flushParagraph()
                else -> paragraphBuffer += line
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    private fun parseInline(text: String): List<MarkdownInline> {
        val result = mutableListOf<MarkdownInline>()
        var lastIndex = 0
        for (match in inlineRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                result += MarkdownInline.Plain(text.substring(lastIndex, match.range.first))
            }
            val code = match.groups[1]?.value
            val bold = match.groups[2]?.value
            when {
                code != null -> result += MarkdownInline.Code(code)
                bold != null -> result += MarkdownInline.Bold(bold)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            result += MarkdownInline.Plain(text.substring(lastIndex))
        }
        if (result.isEmpty()) {
            result += MarkdownInline.Plain(text)
        }
        return result
    }
}

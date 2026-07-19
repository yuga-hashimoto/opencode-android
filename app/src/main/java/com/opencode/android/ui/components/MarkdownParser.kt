package com.opencode.android.ui.components

sealed interface InlineSpan {
    data class Plain(val text: String) : InlineSpan
    data class Bold(val text: String) : InlineSpan
    data class Italic(val text: String) : InlineSpan
    data class Code(val text: String) : InlineSpan
}

sealed interface MarkdownBlock {
    data class Paragraph(val lines: List<List<InlineSpan>>) : MarkdownBlock
    data class Heading(val level: Int, val spans: List<InlineSpan>) : MarkdownBlock
    data class BulletList(val items: List<List<InlineSpan>>) : MarkdownBlock
    data class NumberedList(val items: List<List<InlineSpan>>) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Minimal, dependency-free Markdown-ish parser tuned for LLM chat output: fenced code blocks,
 * headings, bullet/numbered lists, and inline bold/italic/code. Not a full CommonMark
 * implementation — unmatched delimiters degrade gracefully to plain text.
 */
object MarkdownParser {
    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^[-*+]\\s+(.*)$")
    private val numberedRegex = Regex("^\\d+[.)]\\s+(.*)$")
    private val fenceRegex = Regex("^```\\s*([\\w+#.-]*)\\s*$")
    private val hrRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    fun parse(text: String): List<MarkdownBlock> {
        val lines = text.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var paragraphLines = mutableListOf<String>()
        var bulletItems = mutableListOf<String>()
        var numberedItems = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphLines.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraphLines.map(::parseInline))
                paragraphLines = mutableListOf()
            }
        }
        fun flushBullets() {
            if (bulletItems.isNotEmpty()) {
                blocks += MarkdownBlock.BulletList(bulletItems.map(::parseInline))
                bulletItems = mutableListOf()
            }
        }
        fun flushNumbers() {
            if (numberedItems.isNotEmpty()) {
                blocks += MarkdownBlock.NumberedList(numberedItems.map(::parseInline))
                numberedItems = mutableListOf()
            }
        }
        fun flushAll() {
            flushParagraph()
            flushBullets()
            flushNumbers()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fenceMatch = fenceRegex.find(line)
            if (fenceMatch != null) {
                flushAll()
                val lang = fenceMatch.groupValues[1].takeIf { it.isNotBlank() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && fenceRegex.find(lines[i]) == null) {
                    codeLines += lines[i]
                    i++
                }
                if (i < lines.size) i++
                blocks += MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n"))
                continue
            }
            val headingMatch = headingRegex.find(line)
            if (headingMatch != null) {
                flushAll()
                blocks += MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    spans = parseInline(headingMatch.groupValues[2])
                )
                i++
                continue
            }
            if (line.isNotBlank() && hrRegex.matches(line.trim())) {
                flushAll()
                blocks += MarkdownBlock.Divider
                i++
                continue
            }
            val bulletMatch = bulletRegex.find(line)
            if (bulletMatch != null) {
                flushParagraph()
                flushNumbers()
                bulletItems += bulletMatch.groupValues[1]
                i++
                continue
            }
            val numberedMatch = numberedRegex.find(line)
            if (numberedMatch != null) {
                flushParagraph()
                flushBullets()
                numberedItems += numberedMatch.groupValues[1]
                i++
                continue
            }
            if (line.isBlank()) {
                flushAll()
                i++
                continue
            }
            flushBullets()
            flushNumbers()
            paragraphLines += line
            i++
        }
        flushAll()
        return blocks
    }

    private val boldDelims = listOf("**", "__")
    private val italicDelims = listOf("*", "_")

    fun parseInline(line: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans += InlineSpan.Plain(plain.toString())
                plain.clear()
            }
        }

        var i = 0
        val n = line.length
        while (i < n) {
            if (line[i] == '`') {
                val end = line.indexOf('`', i + 1)
                if (end > i) {
                    flushPlain()
                    spans += InlineSpan.Code(line.substring(i + 1, end))
                    i = end + 1
                    continue
                }
            }
            val boldDelim = boldDelims.firstOrNull { line.startsWith(it, i) }
            if (boldDelim != null) {
                val end = line.indexOf(boldDelim, i + boldDelim.length)
                if (end > i) {
                    flushPlain()
                    spans += InlineSpan.Bold(line.substring(i + boldDelim.length, end))
                    i = end + boldDelim.length
                    continue
                }
            }
            val italicDelim = italicDelims.firstOrNull { line.startsWith(it, i) }
            if (italicDelim != null) {
                val end = line.indexOf(italicDelim, i + 1)
                if (end > i) {
                    flushPlain()
                    spans += InlineSpan.Italic(line.substring(i + 1, end))
                    i = end + 1
                    continue
                }
            }
            plain.append(line[i])
            i++
        }
        flushPlain()
        return spans
    }
}

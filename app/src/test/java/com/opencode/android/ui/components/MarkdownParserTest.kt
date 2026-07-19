package com.opencode.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `plain paragraph produces a single paragraph block`() {
        val blocks = MarkdownParser.parse("Hello world")
        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as MarkdownBlock.Paragraph
        assertEquals(listOf(InlineSpan.Plain("Hello world")), paragraph.lines.single())
    }

    @Test
    fun `blank line separates paragraphs`() {
        val blocks = MarkdownParser.parse("First\n\nSecond")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `fenced code block captures language and body verbatim`() {
        val text = "```kotlin\nval x = 1\nval y = 2\n```"
        val blocks = MarkdownParser.parse(text)
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nval y = 2", code.code)
    }

    @Test
    fun `unterminated fenced code block still captures remaining lines`() {
        val text = "```\nrogue block"
        val blocks = MarkdownParser.parse(text)
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals(null, code.language)
        assertEquals("rogue block", code.code)
    }

    @Test
    fun `code inside a fence is not interpreted as markdown`() {
        val text = "```\n**not bold**\n```"
        val blocks = MarkdownParser.parse(text)
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("**not bold**", code.code)
    }

    @Test
    fun `heading level matches hash count`() {
        val blocks = MarkdownParser.parse("## Section title")
        val heading = blocks.single() as MarkdownBlock.Heading
        assertEquals(2, heading.level)
        assertEquals(listOf(InlineSpan.Plain("Section title")), heading.spans)
    }

    @Test
    fun `consecutive bullet lines group into one list`() {
        val blocks = MarkdownParser.parse("- one\n- two\n- three")
        val list = blocks.single() as MarkdownBlock.BulletList
        assertEquals(3, list.items.size)
        assertEquals(listOf(InlineSpan.Plain("two")), list.items[1])
    }

    @Test
    fun `numbered list is distinguished from bullet list`() {
        val blocks = MarkdownParser.parse("1. first\n2. second")
        val list = blocks.single() as MarkdownBlock.NumberedList
        assertEquals(2, list.items.size)
    }

    @Test
    fun `bold and italic and inline code spans parse correctly`() {
        val spans = MarkdownParser.parseInline("plain **bold** and *italic* and `code`")
        assertEquals(
            listOf(
                InlineSpan.Plain("plain "),
                InlineSpan.Bold("bold"),
                InlineSpan.Plain(" and "),
                InlineSpan.Italic("italic"),
                InlineSpan.Plain(" and "),
                InlineSpan.Code("code")
            ),
            spans
        )
    }

    @Test
    fun `unmatched delimiter degrades to plain text instead of crashing`() {
        val spans = MarkdownParser.parseInline("this *has an unmatched star")
        assertEquals(listOf(InlineSpan.Plain("this *has an unmatched star")), spans)
    }

    @Test
    fun `underscore variants of bold and italic are supported`() {
        val spans = MarkdownParser.parseInline("__bold__ and _italic_")
        assertEquals(
            listOf(
                InlineSpan.Bold("bold"),
                InlineSpan.Plain(" and "),
                InlineSpan.Italic("italic")
            ),
            spans
        )
    }

    @Test
    fun `mixed content produces paragraph then code block then list in order`() {
        val text = "Here is the fix:\n\n```python\nprint(1)\n```\n\n- done\n- verified"
        val blocks = MarkdownParser.parse(text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.CodeBlock)
        assertTrue(blocks[2] is MarkdownBlock.BulletList)
    }
}

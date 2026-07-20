package com.opencode.android.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHandoffTest {

    private fun userMessage(text: String) = ChatMessage(
        isUser = true,
        parts = listOf(ChatPart.Text(id = "u-${text.hashCode()}", text = text))
    )

    private fun assistantMessage(text: String) = ChatMessage(
        isUser = false,
        parts = listOf(ChatPart.Text(id = "a-${text.hashCode()}", text = text))
    )

    @Test
    fun `includes header and formats roles`() {
        val messages = listOf(
            userMessage("Hello there"),
            assistantMessage("Hi, how can I help?")
        )

        val prompt = buildHandoffPrompt(messages)

        assertTrue(prompt.startsWith("以下は別の実行先から引き継いだ会話の要約です。続きから対応してください。"))
        assertTrue(prompt.contains("ユーザー: Hello there"))
        assertTrue(prompt.contains("アシスタント: Hi, how can I help?"))
    }

    @Test
    fun `skips messages with no text parts`() {
        val messages = listOf(
            userMessage("Real question"),
            ChatMessage(
                isUser = false,
                parts = listOf(
                    ChatPart.Tool(id = "t1", name = "bash", status = ToolStatus.COMPLETED)
                )
            ),
            assistantMessage("Answer")
        )

        val prompt = buildHandoffPrompt(messages)

        assertTrue(prompt.contains("ユーザー: Real question"))
        assertTrue(prompt.contains("アシスタント: Answer"))
        assertFalse(prompt.contains("bash"))
    }

    @Test
    fun `skips blank text messages`() {
        val messages = listOf(
            userMessage("   "),
            assistantMessage("Kept")
        )

        val prompt = buildHandoffPrompt(messages)

        assertFalse(prompt.contains("ユーザー:"))
        assertTrue(prompt.contains("アシスタント: Kept"))
    }

    @Test
    fun `truncates from the oldest side and keeps most recent messages`() {
        val messages = (1..20).map { index ->
            if (index % 2 == 1) userMessage("user message number $index with some padding text")
            else assistantMessage("assistant message number $index with some padding text")
        }

        val prompt = buildHandoffPrompt(messages, maxChars = 300)

        assertTrue(prompt.length <= 300 + 200)
        assertTrue(prompt.contains("message number 20"))
        assertFalse(prompt.contains("message number 1 "))
    }

    @Test
    fun `always keeps at least the most recent message even if it alone exceeds maxChars`() {
        val longText = "x".repeat(500)
        val messages = listOf(
            userMessage("short earlier message"),
            assistantMessage(longText)
        )

        val prompt = buildHandoffPrompt(messages, maxChars = 50)

        assertTrue(prompt.contains(longText))
        assertFalse(prompt.contains("short earlier message"))
    }

    @Test
    fun `returns just the header when there is nothing to transcribe`() {
        val prompt = buildHandoffPrompt(emptyList())

        assertEquals("以下は別の実行先から引き継いだ会話の要約です。続きから対応してください。", prompt)
    }
}

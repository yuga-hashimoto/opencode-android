package com.opencode.android.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatErrorPresentationTest {
    @Test
    fun `classifies missing local runtime as setup required`() {
        assertEquals(
            ChatErrorKind.RUNTIME_NOT_READY,
            classifyChatError("Android local OpenCode runtime is not installed")
        )
    }

    @Test
    fun `classifies unconfigured connection as setup required`() {
        assertEquals(
            ChatErrorKind.RUNTIME_NOT_READY,
            classifyChatError("OpenCode connection is not configured")
        )
    }

    @Test
    fun `keeps unrelated failures as generic errors`() {
        assertEquals(
            ChatErrorKind.GENERIC,
            classifyChatError("Request timed out")
        )
    }

    @Test
    fun `returns null when no visible error exists`() {
        assertNull(classifyChatError(null))
        assertNull(classifyChatError("   "))
    }
}

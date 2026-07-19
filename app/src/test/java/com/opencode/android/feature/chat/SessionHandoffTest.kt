package com.opencode.android.feature.chat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.OpenCodeMessageInfo
import com.opencode.android.core.api.OpenCodeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHandoffTest {
    private val gson = Gson()

    @Test
    fun `builds markdown transcript from messages`() {
        val messages = listOf(
            message("m1", "user", "Fix the bug"),
            message("m2", "assistant", "Looking into it."),
            message("m3", "user", "")
        )
        val pack = SessionHandoff.buildPackage(
            sourceRuntimeId = "local-android",
            sourceRuntimeName = "Phone",
            sessionId = "sess-1",
            sessionTitle = "Bugfix",
            directory = "/repo",
            messages = messages
        )
        assertEquals("sess-1", pack.sessionId)
        assertEquals("Bugfix", pack.sessionTitle)
        assertEquals(3, pack.messageCount)
        assertTrue(pack.transcript.contains("### user"))
        assertTrue(pack.transcript.contains("Fix the bug"))
        assertTrue(pack.transcript.contains("Looking into it."))
        assertTrue(!pack.transcript.contains("### user\n\n"))
    }

    @Test
    fun `json round trip preserves fields`() {
        val pack = SessionHandoffPackage(
            sourceRuntimeId = "remote-pc",
            sourceRuntimeName = "Mac",
            sessionId = "s2",
            sessionTitle = "Refactor",
            directory = "/work",
            transcript = "hello",
            messageCount = 4
        )
        val raw = SessionHandoff.toJson(pack)
        val decoded = SessionHandoff.fromJson(raw)
        assertEquals(pack, decoded)
    }

    @Test
    fun `json output is parseable by gson`() {
        val pack = SessionHandoffPackage(
            sourceRuntimeId = "remote-pc",
            sourceRuntimeName = "Mac",
            sessionId = "s2",
            sessionTitle = "Refactor",
            directory = "/work",
            transcript = "hello",
            messageCount = 4
        )
        val raw = SessionHandoff.toJson(pack)
        // Round-trip via fromJson to ensure gson can parse our payload.
        val decoded = SessionHandoff.fromJson(raw)
        assertEquals("Mac", decoded.sourceRuntimeName)
        assertEquals("s2", decoded.sessionId)
        assertEquals(4, decoded.messageCount)
    }

    @Test
    fun `unsupported format version is rejected`() {
        val raw = """{
            "version":2,
            "payload":{
                "sourceRuntimeId":"remote-pc",
                "sourceRuntimeName":"Mac",
                "sessionId":"s2",
                "sessionTitle":"Refactor",
                "directory":"/work",
                "transcript":"hello",
                "messageCount":4
            }
        }""".trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            SessionHandoff.fromJson(raw)
        }
    }

    private fun message(id: String, role: String, text: String): OpenCodeMessage =
        OpenCodeMessage(
            info = OpenCodeMessageInfo(
                id = id,
                sessionId = "sess-1",
                role = role,
                time = OpenCodeTime(created = 1L, updated = 2L)
            ),
            parts = if (text.isBlank()) emptyList()
            else listOf(
                com.opencode.android.core.api.OpenCodePart(
                    id = "$id-part",
                    type = "text",
                    text = text
                )
            )
        )
}

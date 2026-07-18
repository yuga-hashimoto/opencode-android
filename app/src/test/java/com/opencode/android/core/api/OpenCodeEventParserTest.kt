package com.opencode.android.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeEventParserTest {
    private val parser = OpenCodeEventParser()

    @Test
    fun `parses server connected event`() {
        val event = parser.parse("""{"type":"server.connected","properties":{}}""")
        assertTrue(event is OpenCodeEvent.ServerConnected)
    }

    @Test
    fun `parses streamed text part update`() {
        val event = parser.parse(
            """{"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"s1","messageID":"m1","type":"text","text":"Hello"}}}"""
        ) as OpenCodeEvent.MessagePartUpdated

        assertEquals("s1", event.part.sessionId)
        assertEquals("Hello", event.part.text)
    }

    @Test
    fun `parses permission request`() {
        val event = parser.parse(
            """{"type":"permission.asked","properties":{"id":"perm1","sessionID":"s1","permission":"bash","patterns":["git status"]}}"""
        ) as OpenCodeEvent.PermissionAsked

        assertEquals("perm1", event.request.id)
        assertEquals("bash", event.request.permission)
        assertEquals(listOf("git status"), event.request.patterns)
    }

    @Test
    fun `parses session idle event`() {
        val event = parser.parse(
            """{"type":"session.idle","properties":{"sessionID":"s1"}}"""
        ) as OpenCodeEvent.SessionIdle

        assertEquals("s1", event.sessionId)
    }

    @Test
    fun `keeps unknown event without crashing`() {
        val event = parser.parse("""{"type":"future.event","properties":{"value":1}}""")
        assertTrue(event is OpenCodeEvent.Unknown)
        assertEquals("future.event", (event as OpenCodeEvent.Unknown).type)
    }
}

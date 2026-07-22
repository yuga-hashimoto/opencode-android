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
    fun `parses streamed text delta`() {
        val event = parser.parse(
            """{"type":"message.part.delta","properties":{"sessionID":"s1","messageID":"m1","partID":"p1","field":"text","delta":"Hello"}}"""
        ) as OpenCodeEvent.MessagePartDelta

        assertEquals("s1", event.sessionId)
        assertEquals("m1", event.messageId)
        assertEquals("p1", event.partId)
        assertEquals("text", event.field)
        assertEquals("Hello", event.delta)
    }

    @Test
    fun `parses tool part update preserving state map`() {
        val event = parser.parse(
            """{"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"s1","messageID":"m1","type":"tool","tool":"bash","callID":"call-1","state":{"status":"running","input":{"command":"ls -la"}}}}}"""
        ) as OpenCodeEvent.MessagePartUpdated

        assertEquals("tool", event.part.type)
        assertEquals("bash", event.part.tool)
        assertEquals("call-1", event.part.callID)
        assertEquals("running", event.part.state?.get("status"))
        val input = event.part.state?.get("input") as Map<*, *>
        assertEquals("ls -la", input["command"])
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

    @Test
    fun `parses question asked with options`() {
        val event = parser.parse(
            """{"type":"question.asked","properties":{"id":"q-1","sessionID":"s1","multiple":true,"questions":[{"question":"Pick a folder","header":"Folder","options":[{"label":"src","description":"Source code"},{"label":"docs"}],"placeholder":"Type a path"}]}}"""
        ) as OpenCodeEvent.QuestionAsked

        val request = event.request
        assertEquals("q-1", request.id)
        assertEquals("s1", request.sessionId)
        assertTrue(request.multiple)
        assertEquals("Pick a folder", request.questions.single().question)
        assertEquals("Folder", request.questions.single().header)
        assertEquals("Type a path", request.questions.single().placeholder)
        assertEquals(listOf("src", "docs"), request.questions.single().options.map { it.label })
        assertEquals("Source code", request.questions.single().options.first().description)
    }

    @Test
    fun `parses question asked with primitive string prompts`() {
        val event = parser.parse(
            """{"type":"question.asked","properties":{"id":"q-2","sessionID":"s1","questions":["Continue?"]}}"""
        ) as OpenCodeEvent.QuestionAsked

        assertEquals("Continue?", event.request.questions.single().question)
        assertTrue(event.request.questions.single().options.isEmpty())
        assertTrue(!event.request.multiple)
    }

    @Test
    fun `malformed question event becomes unknown instead of throwing`() {
        val event = parser.parse("""{"type":"question.asked","properties":{}}""")
        assertTrue(event is OpenCodeEvent.Unknown)
        assertEquals("question.asked", (event as OpenCodeEvent.Unknown).type)
    }

    @Test
    fun `question event with no valid nested prompts becomes unknown`() {
        val event = parser.parse(
            """{"type":"question.asked","properties":{"id":"q-3","sessionID":"s1","questions":[{},{"options":[{"description":"Missing label"}]},42]}}"""
        )

        assertTrue(event is OpenCodeEvent.Unknown)
        assertEquals("question.asked", (event as OpenCodeEvent.Unknown).type)
    }
}

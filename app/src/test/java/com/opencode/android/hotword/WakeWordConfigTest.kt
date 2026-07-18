package com.opencode.android.hotword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordConfigTest {
    @Test
    fun `normalizes capitalization and whitespace`() {
        assertEquals("open code", WakeWordConfig.normalize("  Open   Code  "))
    }

    @Test
    fun `blank phrase falls back to open code`() {
        assertEquals("open code", WakeWordConfig.normalize("   "))
    }

    @Test
    fun `creates valid Vosk grammar json`() {
        val grammar = WakeWordConfig.grammarJson("Open Code")
        assertEquals("[\"open code\", \"[unk]\"]", grammar)
        assertTrue(grammar.contains("open code"))
    }
}

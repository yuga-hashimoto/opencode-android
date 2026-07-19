package com.opencode.android.feature.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordListeningServiceTest {
    @Test
    fun `matches normalized whole wake phrase`() {
        val phrases = listOf("hey open code", "オープンコード")

        assertTrue(matchesWakePhrase("Hey, Open Code!", phrases))
        assertTrue(matchesWakePhrase("please hey open code now", phrases))
        assertTrue(matchesWakePhrase("オープンコード", phrases))
    }

    @Test
    fun `does not match partial word or unrelated phrase`() {
        val phrases = listOf("open code")

        assertFalse(matchesWakePhrase("opencodebase", phrases))
        assertFalse(matchesWakePhrase("open coding", phrases))
        assertFalse(matchesWakePhrase("", phrases))
    }
}

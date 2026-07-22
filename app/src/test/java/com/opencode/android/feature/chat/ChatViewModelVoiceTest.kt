package com.opencode.android.feature.chat

import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelVoiceTest {
    private val viewModel = ChatViewModel(eventFlow = emptyFlow())

    @Test
    fun `rms values become bounded voice levels`() {
        viewModel.updateSpeechRms(-30f)
        assertEquals(0.5f, viewModel.uiState.value.voiceLevel, 0.01f)

        viewModel.updateSpeechRms(20f)
        assertEquals(1f, viewModel.uiState.value.voiceLevel, 0f)
    }

    @Test
    fun `voice state resets across listen process stop and error`() {
        viewModel.startListening()
        viewModel.updateSpeechRms(-15f)
        viewModel.updateSpeechPartial("hello")

        assertTrue(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("hello", viewModel.uiState.value.partialText)

        viewModel.showSpeechProcessing()
        assertFalse(viewModel.uiState.value.isListening)
        assertTrue(viewModel.uiState.value.isSpeechProcessing)
        assertEquals(0f, viewModel.uiState.value.voiceLevel, 0f)
        assertEquals("", viewModel.uiState.value.partialText)

        viewModel.stopListening()
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals(0f, viewModel.uiState.value.voiceLevel, 0f)

        viewModel.startListening()
        viewModel.updateSpeechRms(-5f)
        viewModel.reportSpeechError("mic failed")
        assertFalse(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals(0f, viewModel.uiState.value.voiceLevel, 0f)
        assertEquals("mic failed", viewModel.uiState.value.error)
    }
}

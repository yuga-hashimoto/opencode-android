package com.opencode.android.feature.chat

import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelVoiceTest {
    private val viewModel = ChatViewModel(eventFlow = emptyFlow())

    @Test
    fun `voice transcript remains available after listening stops`() {
        viewModel.startListening()
        viewModel.updateSpeechPartial("hello")

        assertTrue(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("hello", viewModel.uiState.value.partialText)

        viewModel.showSpeechProcessing()
        assertFalse(viewModel.uiState.value.isListening)
        assertTrue(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("hello", viewModel.uiState.value.partialText)

        viewModel.stopListening()
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("hello", viewModel.uiState.value.partialText)
    }

    @Test
    fun `voice error stops listening without discarding transcript`() {
        viewModel.startListening()
        viewModel.updateSpeechPartial("hello")

        viewModel.reportSpeechError("mic failed")

        assertFalse(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("hello", viewModel.uiState.value.partialText)
        assertEquals("mic failed", viewModel.uiState.value.error)
    }

    @Test
    fun `new session resets voice state`() {
        viewModel.startListening()
        viewModel.showSpeechProcessing()

        viewModel.newSession()

        assertFalse(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isSpeechProcessing)
        assertEquals("", viewModel.uiState.value.partialText)
    }
}

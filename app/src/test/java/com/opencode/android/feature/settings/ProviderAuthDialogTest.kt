package com.opencode.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderAuthDialogTest {
    @Test
    fun `extracts short confirmation code from instructions`() {
        assertEquals("ABCD-1234", confirmationCode("Open the browser and enter code: ABCD-1234"))
    }

    @Test
    fun `does not treat a sentence as confirmation code`() {
        assertNull(confirmationCode("Complete authorization in your browser."))
    }
}

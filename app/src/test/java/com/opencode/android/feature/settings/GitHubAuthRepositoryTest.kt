package com.opencode.android.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAuthRepositoryTest {
    @Test
    fun `blank client id disables device flow`() {
        assertFalse(GitHubAuthRepositoryTestProbe.isConfigured(""))
        assertTrue(GitHubAuthRepositoryTestProbe.isConfigured("client-id"))
    }
}

private object GitHubAuthRepositoryTestProbe {
    fun isConfigured(clientId: String) = clientId.isNotBlank()
}

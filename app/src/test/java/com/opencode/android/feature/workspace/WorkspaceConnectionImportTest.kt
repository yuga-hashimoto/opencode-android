package com.opencode.android.feature.workspace

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.feature.connection.DiscoveredOpenCodeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceConnectionImportTest {
    @Test
    fun `QR matching existing endpoint keeps id and saved credentials`() {
        val existing = ConnectionProfile(
            id = "saved-id",
            name = "My Mac",
            baseUrl = "http://192.168.1.20:4096/",
            username = "opencode",
            password = "secret",
            allowInsecureLan = true
        )

        val result = resolveQrConnectionForm(
            raw = "opencode://connect?url=192.168.1.20%3A4096&name=Office&lan=1",
            existingProfiles = listOf(existing)
        ).getOrThrow()

        assertEquals("saved-id", result.id)
        assertEquals("secret", result.password)
        assertEquals("Office", result.name)
        assertTrue(result.allowInsecureLan)
    }

    @Test
    fun `new QR endpoint gets a new form`() {
        val result = resolveQrConnectionForm(
            raw = "https://opencode.example.com:4096",
            existingProfiles = emptyList()
        ).getOrThrow()

        assertEquals("https://opencode.example.com:4096/", result.baseUrl)
        assertTrue(result.id.isNotBlank())
    }

    @Test
    fun `discovered service reuses existing id and credentials`() {
        val existing = ConnectionProfile(
            id = "saved-id",
            name = "Old name",
            baseUrl = "http://192.168.1.30:4096/",
            username = "custom-user",
            password = "secret",
            allowInsecureLan = true
        )
        val service = DiscoveredOpenCodeService(
            name = "Office Linux",
            baseUrl = "http://192.168.1.30:4096/",
            host = "192.168.1.30",
            port = 4096,
            version = "1.18.3"
        )

        val profile = profileFromDiscoveredService(service, listOf(existing)) { "new-id" }

        assertEquals("saved-id", profile.id)
        assertEquals("Office Linux", profile.name)
        assertEquals("custom-user", profile.username)
        assertEquals("secret", profile.password)
        assertTrue(profile.allowInsecureLan)
    }

    @Test
    fun `new discovered HTTPS service does not enable insecure LAN`() {
        val service = DiscoveredOpenCodeService(
            name = "Cloud",
            baseUrl = "https://opencode.example.com/",
            host = "opencode.example.com",
            port = 443
        )

        val profile = profileFromDiscoveredService(service, emptyList()) { "generated-id" }

        assertEquals("generated-id", profile.id)
        assertEquals("https://opencode.example.com/", profile.baseUrl)
        assertEquals(false, profile.allowInsecureLan)
    }
}

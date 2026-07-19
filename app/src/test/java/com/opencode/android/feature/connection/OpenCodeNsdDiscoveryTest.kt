package com.opencode.android.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeNsdDiscoveryTest {
    @Test
    fun `maps trusted IPv4 service and TXT metadata`() {
        val service = mapResolvedOpenCodeService(
            ResolvedOpenCodeService(
                name = "Office Mac",
                host = "192.168.1.20",
                port = 4096,
                attributes = mapOf("api" to "1", "version" to "1.18.3")
            )
        )

        requireNotNull(service)
        assertEquals("Office Mac", service.name)
        assertEquals("http://192.168.1.20:4096/", service.baseUrl)
        assertEquals("1.18.3", service.version)
    }

    @Test
    fun `maps IPv6 and TLS service`() {
        val service = mapResolvedOpenCodeService(
            ResolvedOpenCodeService(
                name = "Linux",
                host = "fd00::1234%wlan0",
                port = 443,
                attributes = mapOf("api" to "1", "tls" to "true")
            )
        )

        requireNotNull(service)
        assertEquals("https://[fd00::1234]/", service.baseUrl)
        assertEquals("fd00::1234", service.host)
    }

    @Test
    fun `rejects unsupported API invalid port and public cleartext host`() {
        assertNull(
            mapResolvedOpenCodeService(
                ResolvedOpenCodeService("Old", "192.168.1.2", 4096, mapOf("api" to "2"))
            )
        )
        assertNull(mapResolvedOpenCodeService(ResolvedOpenCodeService("Bad", "192.168.1.2", 0)))
        assertNull(mapResolvedOpenCodeService(ResolvedOpenCodeService("Public", "8.8.8.8", 4096)))
    }

    @Test
    fun `deduplicates by endpoint and sorts by name`() {
        val first = DiscoveredOpenCodeService("Zeta", "http://192.168.1.2:4096/", "192.168.1.2", 4096)
        val replacement = first.copy(name = "Beta", version = "2")
        val alpha = DiscoveredOpenCodeService("Alpha", "http://192.168.1.3:4096/", "192.168.1.3", 4096)

        val merged = mergeDiscoveredOpenCodeServices(listOf(first, alpha, replacement))

        assertEquals(listOf("Alpha", "Beta"), merged.map { it.name })
        assertEquals("2", merged.last().version)
        assertTrue(merged.map { it.baseUrl }.distinct().size == merged.size)
    }
}

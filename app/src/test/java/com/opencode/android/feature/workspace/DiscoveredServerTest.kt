package com.opencode.android.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveredServerTest {
    @Test
    fun `baseUrl combines host and port as http url`() {
        val server = DiscoveredServer(name = "Mac mini", host = "192.168.1.10", port = 4096)
        assertEquals("http://192.168.1.10:4096", server.baseUrl)
    }

    @Test
    fun `baseUrl works with ipv6 style hosts`() {
        val server = DiscoveredServer(name = "Server", host = "fe80::1", port = 4096)
        assertEquals("http://fe80::1:4096", server.baseUrl)
    }
}

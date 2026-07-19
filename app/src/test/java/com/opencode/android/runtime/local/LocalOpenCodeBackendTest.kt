package com.opencode.android.runtime.local

import com.opencode.android.data.connection.ConnectionProfile
import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class LocalOpenCodeBackendTest {
    @Test
    fun `reuses backend for same port and rebuilds after invalidate or port change`() {
        var port = 4097
        val created = mutableListOf<RemoteOpenCodeBackend>()
        val backend = LocalOpenCodeBackend(
            portProvider = { port },
            backendFactory = { profile ->
                RemoteOpenCodeBackend(profile).also { created += it }
            }
        )

        val first = backend.delegate()
        val second = backend.delegate()
        assertSame(first, second)
        assertEquals(1, created.size)

        backend.invalidate()
        val third = backend.delegate()
        assertNotSame(first, third)
        assertEquals(2, created.size)

        port = 4098
        val fourth = backend.delegate()
        assertNotSame(third, fourth)
        assertEquals(3, created.size)
    }
}

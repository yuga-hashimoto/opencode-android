package com.opencode.android.runtime.local

import com.opencode.android.runtime.remote.RemoteOpenCodeBackend
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOpenCodeBackendConcurrencyTest {

    @Test
    fun `delegate is thread-safe under concurrent access`() {
        val created = java.util.concurrent.atomic.AtomicInteger(0)
        val backend = LocalOpenCodeBackend(
            portProvider = { 8080 },
            backendFactory = { profile ->
                created.incrementAndGet()
                Thread.sleep(50)
                RemoteOpenCodeBackend(profile)
            }
        )

        val threads = 10
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val results = java.util.concurrent.ConcurrentHashMap.newKeySet<RemoteOpenCodeBackend>()

        repeat(threads) {
            executor.submit {
                try {
                    results.add(backend.delegate())
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, results.size)
        assertEquals(1, created.get())
    }
}

package com.opencode.android.runtime.local

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimeAccessCoordinatorTest {
    @Test
    fun `reader waits while writer owns runtime`() {
        val coordinator = LocalRuntimeAccessCoordinator()
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val readerFinished = CountDownLatch(1)

        val writer = thread(start = true, name = "runtime-writer") {
            coordinator.write {
                writerEntered.countDown()
                releaseWriter.await(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(writerEntered.await(2, TimeUnit.SECONDS))

        val reader = thread(start = true, name = "runtime-reader") {
            coordinator.read { readerFinished.countDown() }
        }

        assertFalse(readerFinished.await(100, TimeUnit.MILLISECONDS))
        releaseWriter.countDown()
        assertTrue(readerFinished.await(2, TimeUnit.SECONDS))
        writer.join(2_000)
        reader.join(2_000)
    }
}

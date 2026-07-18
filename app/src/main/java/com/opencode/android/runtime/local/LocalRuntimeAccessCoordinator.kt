package com.opencode.android.runtime.local

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LocalRuntimeAccessCoordinator {
    private val lock = ReentrantReadWriteLock(true)

    fun <T> read(block: () -> T): T = lock.read(block)

    fun <T> write(block: () -> T): T = lock.write(block)
}

package com.rama.blecore.internal.connection

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BleCommandQueue {

    private val mutex = Mutex()

    suspend fun <T> enqueue(action: suspend () -> T): T {
        return mutex.withLock {
            action()
        }
    }
}
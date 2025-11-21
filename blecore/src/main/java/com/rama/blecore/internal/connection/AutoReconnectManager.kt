package com.rama.blecore.internal.connection

import com.rama.blecore.internal.utils.BleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoReconnectManager(
    private val connectionManager: BleConnectionManager,
    private val attempts: Int,
    private val delayMillis: Long
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var stopRequested = false

    fun stop() {
        stopRequested = true
    }

    fun attemptReconnect(address: String) {
        if (stopRequested) return

        scope.launch {
            repeat(attempts) { attempt ->
                if (stopRequested) return@launch

                BleLogger.w("AutoReconnect: Attempt ${attempt + 1} to $address")

                delay(delayMillis)

                val result = connectionManager.connect(address)
                // connection flow will emit states normally

                // If it succeeds, the user will see CONNECTED state
            }

            BleLogger.e("AutoReconnect: Exhausted all attempts for $address")
        }
    }
}
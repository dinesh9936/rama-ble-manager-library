package com.rama.blecore.internal.utils

import kotlinx.coroutines.delay

suspend fun <T> retryIO(
    times: Int,
    delayMillis: Long,
    action: suspend () -> T?
): T? {
    repeat(times - 1) { attempt ->
        val result = action()
        if (result != null) return result

        BleLogger.w("Retrying BLE op… attempt=${attempt + 1}")
        delay(delayMillis)
    }
    return action()
}
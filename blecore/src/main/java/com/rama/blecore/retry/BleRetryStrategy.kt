package com.rama.blecore.retry

import kotlin.time.Duration

interface BleRetryStrategy {

    fun shouldRetry(
        attempt: Int,
        policy: BleRetryPolicy
    ): Boolean

    fun nextDelay(
        attempt: Int,
        policy: BleRetryPolicy
    ): Duration
}
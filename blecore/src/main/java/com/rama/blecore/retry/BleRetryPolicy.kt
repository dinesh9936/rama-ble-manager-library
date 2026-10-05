package com.rama.blecore.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface BleRetryPolicy {

    data object None : BleRetryPolicy

    data class Fixed(
        val maxAttempts: Int,
        val delay: Duration = 1.seconds
    ) : BleRetryPolicy

    data class Exponential(
        val maxAttempts: Int,
        val initialDelay: Duration = 500.milliseconds,
        val maxDelay: Duration = 30.seconds,
        val multiplier: Double = 2.0
    ) : BleRetryPolicy
}
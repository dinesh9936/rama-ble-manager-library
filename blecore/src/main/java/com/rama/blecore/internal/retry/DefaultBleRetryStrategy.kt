package com.rama.blecore.internal.retry

import com.rama.blecore.retry.BleRetryPolicy
import com.rama.blecore.retry.BleRetryStrategy
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

internal class DefaultBleRetryStrategy :
    BleRetryStrategy {

    override fun shouldRetry(
        attempt: Int,
        policy: BleRetryPolicy
    ): Boolean {
        return when (policy) {

            BleRetryPolicy.None ->
                false

            is BleRetryPolicy.Fixed ->
                attempt < policy.maxAttempts

            is BleRetryPolicy.Exponential ->
                attempt < policy.maxAttempts
        }
    }

    override fun nextDelay(
        attempt: Int,
        policy: BleRetryPolicy
    ): Duration {
        return when (policy) {

            BleRetryPolicy.None ->
                ZERO

            is BleRetryPolicy.Fixed ->
                policy.delay

            is BleRetryPolicy.Exponential -> {

                val factor =
                    policy.multiplier.pow(
                        attempt.coerceAtLeast(0)
                    )

                val delay =
                    policy.initialDelay * factor

                minOf(
                    delay,
                    policy.maxDelay
                )
            }
        }
    }
}
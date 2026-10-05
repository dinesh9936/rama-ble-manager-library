package com.rama.blecore.internal.gatt

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.gatt.BleGattEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal class GattOperationCoordinator {

    private val operationMutex =
        Mutex()

    private var pendingOperation:
            PendingOperation<*>? = null

    suspend fun <T> execute(
        timeout: Duration,
        matcher: (BleGattEvent) -> T?,
        errorMatcher: (BleError) -> Boolean = { true },
        starter: () -> Boolean
    ): T {

        return operationMutex.withLock {

            val deferred =
                CompletableDeferred<T>()

            val pending =
                PendingOperation(
                    deferred = deferred,
                    matcher = matcher,
                    errorMatcher = errorMatcher
                )

            pendingOperation = pending

            try {

                val started =
                    starter()

                if (!started) {
                    throw IllegalStateException(
                        "Unable to start GATT operation"
                    )
                }

                withTimeout(timeout) {
                    deferred.await()
                }

            } finally {

                if (pendingOperation === pending) {
                    pendingOperation = null
                }
            }
        }
    }

    fun onEvent(
        event: BleGattEvent
    ) {

        val pending =
            pendingOperation
                ?: return

        when (event) {

            is BleGattEvent.Error -> {

                if (
                    pending.matchesError(
                        event.error
                    )
                ) {

                    pending.fail(
                        BleException(
                            event.error
                        )
                    )

                    pendingOperation = null
                }
            }

            else -> {

                if (
                    pending.tryComplete(
                        event
                    )
                ) {
                    pendingOperation = null
                }
            }
        }
    }

    private class PendingOperation<T>(
        private val deferred:
        CompletableDeferred<T>,
        private val matcher:
            (BleGattEvent) -> T?,
        private val errorMatcher:
            (BleError) -> Boolean
    ) {

        fun tryComplete(
            event: BleGattEvent
        ): Boolean {

            val value =
                matcher(event)
                    ?: return false

            deferred.complete(value)

            return true
        }

        fun matchesError(
            error: BleError
        ): Boolean {
            return errorMatcher(error)
        }

        fun fail(
            throwable: Throwable
        ) {
            deferred.completeExceptionally(
                throwable
            )
        }
    }
}
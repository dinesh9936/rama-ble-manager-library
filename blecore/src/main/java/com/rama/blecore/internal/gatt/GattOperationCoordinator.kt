package com.rama.blecore.internal.gatt

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.gatt.BleGattEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal class GattOperationCoordinator(
    private val onSessionInvalidated: (BleError) -> Unit = {}
) {

    private val operationMutex = Mutex()
    private val pendingLock = Any()

    private var pendingOperation: PendingOperation<*>? = null
    private var connectionFailure: BleException? = null

    suspend fun <T> execute(
        timeout: Duration,
        matcher: (BleGattEvent) -> T?,
        errorMatcher: (BleError) -> Boolean = { true },
        starter: () -> Boolean
    ): T = operationMutex.withLock {

        val deferred = CompletableDeferred<T>()

        val pending = PendingOperation(
            deferred = deferred,
            matcher = matcher,
            errorMatcher = errorMatcher
        )

        var nativeOperationStarted = false

        synchronized(pendingLock) {
            connectionFailure?.let { throw it }
            pendingOperation = pending
        }

        try {
            val coroutineContext = currentCoroutineContext()

            val started = synchronized(pendingLock) {

                connectionFailure?.let { throw it }

                coroutineContext.ensureActive()

                val accepted = try {
                    starter()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: BleException) {
                    throw exception
                } catch (exception: SecurityException) {
                    throw BleException(
                        BleError.PermissionDenied(
                            permissions = emptyList(),
                            message = "Bluetooth permission denied while starting GATT operation"
                        )
                    )
                } catch (exception: Exception) {
                    throw BleException(
                        BleError.Unknown(
                            message = "Unable to start GATT operation: ${exception.message}"
                        )
                    )
                }

                if (accepted) {
                    nativeOperationStarted = true
                }

                accepted
            }

            if (!started) {
                throw BleException(
                    BleError.Unknown(
                        message = "Android rejected the GATT operation before it started"
                    )
                )
            }

            withTimeout(timeout) {
                deferred.await()
            }

        } catch (timeoutException: TimeoutCancellationException) {

            if (nativeOperationStarted) {
                invalidateSession(
                    BleError.Timeout(
                        operation = "gatt",
                        message = "GATT operation timed out"
                    )
                )
            }

            throw timeoutException

        } catch (cancellation: CancellationException) {

            if (nativeOperationStarted) {
                invalidateSession(
                    BleError.ConnectionFailed(
                        deviceAddress = "unknown",
                        message = "GATT operation cancelled while in progress"
                    )
                )
            }

            throw cancellation

        } finally {
            synchronized(pendingLock) {
                if (pendingOperation === pending) {
                    pendingOperation = null
                }
            }
        }
    }

    fun onEvent(event: BleGattEvent) {
        synchronized(pendingLock) {
            val pending = pendingOperation ?: return

            when (event) {
                is BleGattEvent.Error -> {
                    if (pending.matchesError(event.error)) {
                        pending.fail(BleException(event.error))
                        pendingOperation = null
                    }
                }

                else -> {
                    if (pending.tryComplete(event)) {
                        pendingOperation = null
                    }
                }
            }
        }
    }

    fun onConnectionLost(deviceAddress: String) {
        val error = BleError.ConnectionFailed(
            deviceAddress = deviceAddress,
            message = "BLE connection lost during GATT operation"
        )

        synchronized(pendingLock) {
            if (connectionFailure != null) return

            connectionFailure = BleException(error)

            pendingOperation?.fail(connectionFailure!!)
            pendingOperation = null
        }
    }

    private fun invalidateSession(error: BleError) {
        val shouldNotify = synchronized(pendingLock) {
            if (connectionFailure != null) {
                false
            } else {
                connectionFailure = BleException(error)

                pendingOperation?.fail(connectionFailure!!)
                pendingOperation = null

                true
            }
        }

        if (shouldNotify) {
            onSessionInvalidated(error)
        }
    }

    private class PendingOperation<T>(
        private val deferred: CompletableDeferred<T>,
        private val matcher: (BleGattEvent) -> T?,
        private val errorMatcher: (BleError) -> Boolean
    ) {
        fun tryComplete(event: BleGattEvent): Boolean {
            val value = matcher(event) ?: return false
            return deferred.complete(value)
        }

        fun matchesError(error: BleError): Boolean =
            errorMatcher(error)

        fun fail(throwable: Throwable) {
            deferred.completeExceptionally(throwable)
        }
    }
}
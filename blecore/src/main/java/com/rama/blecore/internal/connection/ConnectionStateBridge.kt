package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import kotlinx.coroutines.CompletableDeferred

/**
 * Coordinates Bluetooth callbacks with AndroidBleConnection.
 *
 * Thread-safe: all state transitions are synchronized.
 *
 * A terminal state is final for this connection attempt.
 */
internal class ConnectionStateBridge {

    private enum class Lifecycle {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        FAILED
    }

    private val lock = Any()

    private var lifecycle = Lifecycle.CONNECTING

    private var connection: AndroidBleConnection? = null

    private var terminalReason: BleDisconnectReason? = null

    private var terminalError: BleError? = null
    private var connectionEstablished = false

    /**
     * Completed when the initial connection succeeds.
     * Completed exceptionally when establishment fails.
     */
    val connectionDeferred =
        CompletableDeferred<AndroidBleConnection>()

    /**
     * Completed after local GATT cleanup finishes.
     */
    val cleanupCompleted = CompletableDeferred<Unit>()

    /**
     * Attach the connection object.
     *
     * A callback may arrive before attach() is called.
     */
    fun attach(newConnection: AndroidBleConnection) {
        synchronized(lock) {

            check(connection == null) {
                "Connection already attached"
            }

            connection = newConnection

            when (lifecycle) {

                Lifecycle.CONNECTING -> Unit

                Lifecycle.CONNECTED -> {
                    newConnection.markConnected()

                    connectionDeferred.complete(newConnection)
                }

                Lifecycle.DISCONNECTING -> {
                    // Disconnect already requested.
                    // Do not publish Connected.
                }

                Lifecycle.DISCONNECTED -> {
                    newConnection.markDisconnected(terminalReason)

                    connectionDeferred.completeExceptionally(
                        BleException(
                            BleError.ConnectionFailed(
                                deviceAddress = newConnection.device.address,
                                message = "Device disconnected before connection completed"
                            )
                        )
                    )
                }

                Lifecycle.FAILED -> {
                    val error = checkNotNull(terminalError)

                    newConnection.markFailed(error)

                    connectionDeferred.completeExceptionally(
                        BleException(error)
                    )
                }
            }
        }
    }

    /**
     * Called when Android reports STATE_CONNECTED.
     */
    fun connected() {
        synchronized(lock) {

            if (lifecycle != Lifecycle.CONNECTING) {
                return
            }

            lifecycle = Lifecycle.CONNECTED
            connectionEstablished = true

            connection?.let { current ->
                current.markConnected()
                connectionDeferred.complete(current)
            }
        }
    }

    /**
     * Called when the application initiates disconnect.
     *
     * This blocks subsequent Connected callbacks.
     */
    fun disconnecting() {
        synchronized(lock) {

            if (
                lifecycle == Lifecycle.DISCONNECTED ||
                lifecycle == Lifecycle.FAILED ||
                lifecycle == Lifecycle.DISCONNECTING
            ) {
                return
            }

            lifecycle = Lifecycle.DISCONNECTING
        }
    }

    /**
     * Called when Android reports disconnection.
     */
    fun disconnected(reason: BleDisconnectReason) {
        synchronized(lock) {

            if (
                lifecycle == Lifecycle.DISCONNECTED ||
                lifecycle == Lifecycle.FAILED
            ) {
                return
            }

            lifecycle = Lifecycle.DISCONNECTED
            terminalReason = reason

            connection?.markDisconnected(reason)

            /*
             * If connection establishment is still pending,
             * unblock the caller with a failure.
             */
            if (!connectionDeferred.isCompleted) {
                val address = connection?.device?.address ?: "unknown"

                connectionDeferred.completeExceptionally(
                    BleException(
                        BleError.ConnectionFailed(
                            deviceAddress = address,
                            message = "Device disconnected before connection completed"
                        )
                    )
                )
            }
        }
    }

    /**
     * Called when Android reports a connection failure.
     */
    fun failed(error: BleError) {
        synchronized(lock) {

            if (
                lifecycle == Lifecycle.DISCONNECTED ||
                lifecycle == Lifecycle.FAILED
            ) {
                return
            }

            lifecycle = Lifecycle.FAILED
            terminalError = error

            connection?.markFailed(error)

            if (!connectionDeferred.isCompleted) {
                connectionDeferred.completeExceptionally(
                    BleException(error)
                )
            }
        }
    }

    /**
     * Called after BluetoothGatt.close() has been attempted.
     */
    fun markCleanupCompleted() {
        cleanupCompleted.complete(Unit)
    }

    /**
     * Returns true only when the connection is active.
     *
     * Thread-safe.
     */
    fun isConnected(): Boolean =
        synchronized(lock) {
            lifecycle == Lifecycle.CONNECTED
        }

    /**
     * Returns true if this connection was successfully
     * established at least once.
     *
     * Thread-safe.
     */
    fun wasConnected(): Boolean =
        synchronized(lock) {
            connectionEstablished
        }

    /**
     * Returns true when the connection has reached a
     * terminal lifecycle state.
     */
    fun isTerminal(): Boolean =
        synchronized(lock) {
            lifecycle == Lifecycle.DISCONNECTED ||
                    lifecycle == Lifecycle.FAILED
        }

    /**
     * Atomically handles a non-success GATT connection status.
     *
     * Returns false if the lifecycle is already terminal
     * or a local disconnection is in progress.
     */
    fun handleGattError(
        deviceAddress: String,
        status: Int
    ): Boolean = synchronized(lock) {

        if (
            lifecycle == Lifecycle.DISCONNECTED ||
            lifecycle == Lifecycle.FAILED ||
            lifecycle == Lifecycle.DISCONNECTING
        ) {
            return@synchronized false
        }

        val result = GattConnectionErrorMapper.map(
            deviceAddress = deviceAddress,
            status = status,
            wasConnected = connectionEstablished
        )

        when (result) {

            is GattConnectionErrorMapper.Result.Failed -> {
                failed(result.error)
            }

            is GattConnectionErrorMapper.Result.Disconnected -> {
                disconnected(result.reason)
            }
        }

        true
    }

    /**
     * Completes a disconnection explicitly requested by
     * the consuming application.
     *
     * Idempotent and thread-safe.
     */
    fun completeRequestedDisconnect() {
        synchronized(lock) {

            if (lifecycle != Lifecycle.DISCONNECTING) {
                return
            }

            disconnected(
                BleDisconnectReason.RequestedByUser
            )
        }
    }
}
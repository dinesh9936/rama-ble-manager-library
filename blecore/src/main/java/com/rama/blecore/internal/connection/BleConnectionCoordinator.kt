package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.connection.BleConnectionPolicy
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.connection.BleConnector
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coordinates BLE connections according to the configured policy.
 *
 * MULTI_DEVICE:
 * - Different peripherals can connect simultaneously.
 * - Duplicate connections to the same peripheral are rejected
 *   by the underlying BleConnectionRegistry.
 *
 * SINGLE_DEVICE:
 * - Only one managed connection is active at a time.
 * - Connecting another peripheral disconnects the previous one.
 * - The new connection starts only after local GATT cleanup
 *   and registry ownership release.
 *
 * This coordinator must be shared by all connection requests
 * belonging to the same BleClient instance.
 */
internal class BleConnectionCoordinator(
    private val delegate: BleConnector,
    private val connectionPolicy: BleConnectionPolicy,
    private val disconnectTimeoutMs: Long = 5_000L
) : BleConnector {

    init {
        require(disconnectTimeoutMs > 0L) {
            "disconnectTimeoutMs must be greater than zero"
        }
    }

    private val mutex = Mutex()

    /**
     * Retain the last connection until its cleanup is confirmed.
     *
     * A terminal connection can still have pending GATT cleanup,
     * so we must not discard it based on state alone.
     */
    private var activeConnection: BleConnection? = null

    override suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection {

        return when (connectionPolicy) {

            BleConnectionPolicy.MULTI_DEVICE -> {
                delegate.connect(
                    device = device,
                    config = config
                )
            }

            BleConnectionPolicy.SINGLE_DEVICE -> {
                connectSingleDevice(
                    device = device,
                    config = config
                )
            }
        }
    }

    private suspend fun connectSingleDevice(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection = mutex.withLock {

        val previous = activeConnection

        if (previous != null) {

            val previousState = previous.state.value

            val previousIsTerminal =
                previousState is BleConnectionState.Disconnected ||
                        previousState is BleConnectionState.Failed

            /*
             * Same-device duplicate protection.
             *
             * Do not disconnect Device A when another request
             * attempts to connect Device A again.
             */
            if (
                !previousIsTerminal &&
                previous.device.address.equals(
                    device.address,
                    ignoreCase = true
                )
            ) {
                throw BleException(
                    BleError.AlreadyConnected(
                        device.address
                    )
                )
            }

            /*
             * Even when previousState is terminal, native cleanup
             * or registry release may still be pending.
             */
            releasePreviousConnection(
                connection = previous,
                alreadyTerminal = previousIsTerminal
            )

            activeConnection = null
        }

        /*
         * Only one coroutine can execute this section
         * in SINGLE_DEVICE mode.
         */
        val newConnection = delegate.connect(
            device = device,
            config = config
        )

        activeConnection = newConnection

        newConnection
    }

    /**
     * Ensures the previous connection has released its local
     * resources before a replacement connection is started.
     *
     * The timeout covers:
     * 1. Initiating disconnection, if needed.
     * 2. Local GATT cleanup.
     * 3. Connection registry release.
     */
    private suspend fun releasePreviousConnection(
        connection: BleConnection,
        alreadyTerminal: Boolean
    ) {

        val managedConnection = connection as? ManagedBleConnection
            ?: throw BleException(
                BleError.ConnectionFailed(
                    deviceAddress = connection.device.address,
                    message = "Connection does not support managed cleanup"
                )
            )

        try {
            withTimeout(disconnectTimeoutMs.milliseconds) {

                if (!alreadyTerminal) {
                    connection.disconnect()
                }

                managedConnection.awaitCleanup()

                managedConnection.awaitRegistryRelease()
            }

        } catch (timeout: TimeoutCancellationException) {

            throw BleException(
                BleError.ConnectionFailed(
                    deviceAddress = connection.device.address,
                    message = "Previous BLE connection cleanup timed out " +
                            "after ${disconnectTimeoutMs}ms",
                    cause = timeout
                )
            )

        } catch (cancellation: CancellationException) {

            // Preserve structured concurrency.
            throw cancellation
        }
    }
}
package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.error.BleError
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal lifecycle contract for policy-aware connection management.
 */
internal interface ManagedBleConnection : BleConnection {

    suspend fun awaitCleanup()

    suspend fun awaitRegistryRelease()
}

/**
 * Represents one Android BLE connection.
 *
 * The connector owns native BluetoothGatt cleanup.
 * ConnectionStateBridge coordinates callback-driven state changes.
 */
internal class AndroidBleConnection(
    override val device: BleDevice,
    override val gatt: BleGattClient,
    private val disconnectAction: suspend () -> Unit,
    private val cleanupCompleted: Deferred<Unit>,
    private val stateBridge: ConnectionStateBridge
) : ManagedBleConnection {

    private val disconnectRequested = AtomicBoolean(false)

    private val registryReleased = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow<BleConnectionState>(
        BleConnectionState.Connecting(device)
    )

    override val state: StateFlow<BleConnectionState> =
        _state.asStateFlow()

    /**
     * Called by ConnectionStateBridge.
     */
    internal fun markConnected() {
        if (disconnectRequested.get()) return

        _state.update { current ->
            when (current) {
                is BleConnectionState.Connecting ->
                    BleConnectionState.Connected(device)

                else -> current
            }
        }
    }

    internal fun markDisconnected(
        reason: BleDisconnectReason? = null
    ) {
        _state.update { current ->
            when (current) {
                is BleConnectionState.Disconnected,
                is BleConnectionState.Failed -> current

                else -> BleConnectionState.Disconnected(
                    device = device,
                    reason = reason
                )
            }
        }
    }

    internal fun markFailed(error: BleError) {
        _state.update { current ->
            when (current) {
                is BleConnectionState.Disconnected,
                is BleConnectionState.Failed -> current

                else -> BleConnectionState.Failed(
                    device = device,
                    error = error
                )
            }
        }
    }

    override suspend fun disconnect() {

        if (!disconnectRequested.compareAndSet(false, true)) {
            return
        }

        val current = _state.value

        if (
            current is BleConnectionState.Disconnected ||
            current is BleConnectionState.Failed ||
            current is BleConnectionState.Idle
        ) {
            return
        }

        stateBridge.disconnecting()

        _state.update { state ->
            when (state) {
                is BleConnectionState.Disconnected,
                is BleConnectionState.Failed -> state

                else -> BleConnectionState.Disconnecting(device)
            }
        }

        try {
            disconnectAction()
        } catch (exception: Throwable) {
            // Permit a later disconnect request if initiation failed.
            disconnectRequested.set(false)
            throw exception
        }
    }

    override suspend fun awaitCleanup() {
        cleanupCompleted.await()
    }

    override suspend fun awaitRegistryRelease() {
        registryReleased.await()
    }

    internal fun markRegistryReleased() {
        registryReleased.complete(Unit)
    }
}
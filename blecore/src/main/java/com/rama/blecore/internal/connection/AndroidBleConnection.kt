package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidBleConnection(
    override val device: BleDevice,
    override val gatt: BleGattClient,
    private val disconnectAction: suspend () -> Unit
) : BleConnection {

    private val _state =
        MutableStateFlow<BleConnectionState>(
            BleConnectionState.Connecting(device)
        )

    override val state: StateFlow<BleConnectionState> =
        _state.asStateFlow()

    internal fun updateState(
        state: BleConnectionState
    ) {
        _state.value = state
    }

    internal fun markConnected() {
        updateState(
            BleConnectionState.Connected(device)
        )
    }

    internal fun markDisconnected(
        reason: BleDisconnectReason? = null
    ) {
        updateState(
            BleConnectionState.Disconnected(
                device = device,
                reason = reason
            )
        )
    }

    internal fun markFailed(
        error: com.rama.blecore.error.BleError
    ) {
        updateState(
            BleConnectionState.Failed(
                device = device,
                error = error
            )
        )
    }

    override suspend fun disconnect() {

        val currentState = _state.value

        if (
            currentState is BleConnectionState.Disconnected ||
            currentState is BleConnectionState.Idle
        ) {
            return
        }

        updateState(
            BleConnectionState.Disconnecting(device)
        )

        disconnectAction()
    }
}
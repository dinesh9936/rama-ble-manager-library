package com.rama.blecore.connection

import com.rama.blecore.error.BleError
import com.rama.blecore.model.BleDevice

sealed interface BleConnectionState {

    data object Idle : BleConnectionState

    data class Connecting(
        val device: BleDevice
    ) : BleConnectionState

    data class Connected(
        val device: BleDevice
    ) : BleConnectionState

    data class Disconnecting(
        val device: BleDevice
    ) : BleConnectionState

    data class Disconnected(
        val device: BleDevice,
        val reason: BleDisconnectReason? = null
    ) : BleConnectionState

    data class Failed(
        val device: BleDevice,
        val error: BleError
    ) : BleConnectionState
}
package com.rama.blecore.model

sealed class BleConnectionState{
    object Connecting: BleConnectionState()
    object Connected: BleConnectionState()
    object Disconnecting: BleConnectionState()
    object Disconnected: BleConnectionState()
    data class Failed(val reason: String): BleConnectionState()
}

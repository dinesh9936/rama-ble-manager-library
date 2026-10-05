package com.rama.blecore.connection

import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.flow.StateFlow

interface BleConnection {

    val device: BleDevice

    val state: StateFlow<BleConnectionState>

    val gatt: BleGattClient

    suspend fun disconnect()
}
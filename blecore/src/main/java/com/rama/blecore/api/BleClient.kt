package com.rama.blecore.api

import com.rama.blecore.model.BleConnectionState
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface BleClient {
    fun scanDevices(scanServiceUUID: String = "", scanTimeout: Long = 5_000L, deviceNameStartWith: String = ""): Flow<BleDevice>
    fun connect(address: String): Flow<BleConnectionState>
    fun disconnect()
    suspend fun readCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): ByteArray?
    suspend fun writeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID, data: ByteArray): Boolean
    fun observeCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): Flow<ByteArray>
}
package com.rama.blecore.gatt

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface BleGattClient {

    val events: Flow<BleGattEvent>

    suspend fun discoverServices(): List<BleService>

    suspend fun readCharacteristic(
        characteristicUuid: UUID
    ): ByteArray

    suspend fun writeCharacteristic(
        characteristicUuid: UUID,
        value: ByteArray,
        writeType: BleWriteType = BleWriteType.WITH_RESPONSE
    )

    fun observeCharacteristic(
        characteristicUuid: UUID
    ): Flow<ByteArray>

    suspend fun setNotification(
        characteristicUuid: UUID,
        enabled: Boolean
    )

    suspend fun readDescriptor(
        descriptorUuid: UUID
    ): ByteArray

    suspend fun writeDescriptor(
        descriptorUuid: UUID,
        value: ByteArray
    )

    suspend fun requestMtu(
        mtu: Int
    ): Int

    suspend fun readRssi(): Int
}
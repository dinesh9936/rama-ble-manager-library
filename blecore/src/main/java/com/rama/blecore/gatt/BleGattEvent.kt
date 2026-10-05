package com.rama.blecore.gatt

import com.rama.blecore.error.BleError
import java.util.UUID

sealed interface BleGattEvent {

    data class ServicesDiscovered(
        val services: List<BleService>
    ) : BleGattEvent

    data class CharacteristicRead(
        val characteristicUuid: UUID,
        val value: ByteArray
    ) : BleGattEvent

    data class CharacteristicWritten(
        val characteristicUuid: UUID
    ) : BleGattEvent

    data class CharacteristicChanged(
        val characteristicUuid: UUID,
        val value: ByteArray
    ) : BleGattEvent

    data class DescriptorRead(
        val descriptorUuid: UUID,
        val value: ByteArray
    ) : BleGattEvent

    data class DescriptorWritten(
        val descriptorUuid: UUID
    ) : BleGattEvent

    data class MtuChanged(
        val mtu: Int
    ) : BleGattEvent

    data class RssiRead(
        val rssi: Int
    ) : BleGattEvent

    data class Error(
        val error: BleError
    ) : BleGattEvent
}
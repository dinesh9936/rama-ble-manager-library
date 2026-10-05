package com.rama.blecore.gatt

import java.util.UUID

sealed interface BleGattOperation {

    data object DiscoverServices : BleGattOperation

    data class ReadCharacteristic(
        val characteristicUuid: UUID
    ) : BleGattOperation

    data class WriteCharacteristic(
        val characteristicUuid: UUID,
        val value: ByteArray,
        val writeType: BleWriteType = BleWriteType.WITH_RESPONSE
    ) : BleGattOperation

    data class SetNotification(
        val characteristicUuid: UUID,
        val enabled: Boolean
    ) : BleGattOperation

    data class ReadDescriptor(
        val descriptorUuid: UUID
    ) : BleGattOperation

    data class WriteDescriptor(
        val descriptorUuid: UUID,
        val value: ByteArray
    ) : BleGattOperation

    data class RequestMtu(
        val mtu: Int
    ) : BleGattOperation

    data object ReadRssi : BleGattOperation
}
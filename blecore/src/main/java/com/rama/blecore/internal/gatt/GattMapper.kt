package com.rama.blecore.internal.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.rama.blecore.gatt.BleCharacteristic
import com.rama.blecore.gatt.BleCharacteristicProperty
import com.rama.blecore.gatt.BleDescriptor
import com.rama.blecore.gatt.BleService

internal class GattMapper {

    fun mapServices(
        services: List<BluetoothGattService>
    ): List<BleService> {
        return services.map(::mapService)
    }

    fun mapService(
        service: BluetoothGattService
    ): BleService {
        return BleService(
            uuid = service.uuid,
            characteristics =
                service.characteristics.map(
                    ::mapCharacteristic
                )
        )
    }

    fun mapCharacteristic(
        characteristic: BluetoothGattCharacteristic
    ): BleCharacteristic {
        return BleCharacteristic(
            uuid = characteristic.uuid,
            properties =
                characteristic.properties
                    .toBleProperties(),
            descriptors =
                characteristic.descriptors.map { descriptor ->
                    BleDescriptor(
                        uuid = descriptor.uuid
                    )
                }
        )
    }

    private fun Int.toBleProperties():
            Set<BleCharacteristicProperty> {

        return buildSet {

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_READ != 0
            ) {
                add(
                    BleCharacteristicProperty.READ
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            ) {
                add(
                    BleCharacteristicProperty.WRITE
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            ) {
                add(
                    BleCharacteristicProperty.WRITE_WITHOUT_RESPONSE
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            ) {
                add(
                    BleCharacteristicProperty.NOTIFY
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            ) {
                add(
                    BleCharacteristicProperty.INDICATE
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0
            ) {
                add(
                    BleCharacteristicProperty.BROADCAST
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0
            ) {
                add(
                    BleCharacteristicProperty.SIGNED_WRITE
                )
            }

            if (
                this@toBleProperties and
                BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0
            ) {
                add(
                    BleCharacteristicProperty.EXTENDED_PROPERTIES
                )
            }
        }
    }
}
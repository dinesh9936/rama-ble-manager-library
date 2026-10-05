package com.rama.blecore.internal.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.rama.blecore.error.BleError
import com.rama.blecore.gatt.BleGattEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class GattCallbackHandler(
    private val operationCoordinator: GattOperationCoordinator,
    private val gattMapper: GattMapper,
    private val onConnectionStateChanged: (
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) -> Unit = { _, _, _ -> }
) : BluetoothGattCallback() {

    private val _events =
        MutableSharedFlow<BleGattEvent>(
            extraBufferCapacity = 64
        )

    val events: SharedFlow<BleGattEvent> =
        _events.asSharedFlow()

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) {
        onConnectionStateChanged(
            gatt,
            status,
            newState
        )
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.ServicesDiscovered(
                    services =
                        gattMapper.mapServices(
                            gatt.services
                        )
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.ServiceDiscoveryFailed(
                        status = status
                    )
                )
            )
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.CharacteristicRead(
                    characteristicUuid =
                        characteristic.uuid,
                    value = value
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.ReadFailed(
                        characteristicUuid =
                            characteristic.uuid.toString(),
                        status = status
                    )
                )
            )
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.CharacteristicWritten(
                    characteristicUuid =
                        characteristic.uuid
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.WriteFailed(
                        characteristicUuid =
                            characteristic.uuid.toString(),
                        status = status
                    )
                )
            )
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        emit(
            BleGattEvent.CharacteristicChanged(
                characteristicUuid =
                    characteristic.uuid,
                value = value
            )
        )
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.DescriptorRead(
                    descriptorUuid =
                        descriptor.uuid,
                    value = value
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.DescriptorReadFailed(
                        descriptorUuid =
                            descriptor.uuid.toString(),
                        status = status
                    )
                )
            )
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.DescriptorWritten(
                    descriptorUuid =
                        descriptor.uuid
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.DescriptorWriteFailed(
                        descriptorUuid =
                            descriptor.uuid.toString(),
                        status = status
                    )
                )
            )
        }
    }

    override fun onMtuChanged(
        gatt: BluetoothGatt,
        mtu: Int,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.MtuChanged(
                    mtu = mtu
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.MtuRequestFailed(
                        requestedMtu = mtu,
                        status = status
                    )
                )
            )
        }
    }

    override fun onReadRemoteRssi(
        gatt: BluetoothGatt,
        rssi: Int,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            emit(
                BleGattEvent.RssiRead(
                    rssi = rssi
                )
            )

        } else {

            emit(
                BleGattEvent.Error(
                    BleError.RssiReadFailed(
                        status = status
                    )
                )
            )
        }
    }

    private fun emit(
        event: BleGattEvent
    ) {
        operationCoordinator.onEvent(event)
        _events.tryEmit(event)
    }
}
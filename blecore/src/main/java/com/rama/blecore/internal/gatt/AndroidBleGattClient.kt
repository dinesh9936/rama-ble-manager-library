package com.rama.blecore.internal.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.gatt.BleGattEvent
import com.rama.blecore.gatt.BleService
import com.rama.blecore.gatt.BleWriteType
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.UUID

internal class AndroidBleGattClient(
    private val bluetoothGatt: BluetoothGatt,
    private val callbackHandler: GattCallbackHandler,
    private val operationCoordinator: GattOperationCoordinator,
    private val timeoutConfig: BleTimeoutConfig
) : BleGattClient {

    override val events: Flow<BleGattEvent>
        get() = callbackHandler.events

    @SuppressLint("MissingPermission")
    override suspend fun discoverServices(): List<BleService> {

        return operationCoordinator.execute(
            timeout = timeoutConfig.serviceDiscoveryTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.ServicesDiscovered ->
                        event.services

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.ServiceDiscoveryFailed
            },

            starter = {
                bluetoothGatt.discoverServices()
            }
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun readCharacteristic(
        characteristicUuid: UUID
    ): ByteArray {

        val characteristic =
            findCharacteristic(
                characteristicUuid
            )

        return operationCoordinator.execute(
            timeout = timeoutConfig.readTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.CharacteristicRead -> {
                        if (
                            event.characteristicUuid ==
                            characteristicUuid
                        ) {
                            event.value
                        } else {
                            null
                        }
                    }

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.ReadFailed &&
                        error.characteristicUuid ==
                        characteristicUuid.toString()
            },

            starter = {
                bluetoothGatt.readCharacteristic(
                    characteristic
                )
            }
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCharacteristic(
        characteristicUuid: UUID,
        value: ByteArray,
        writeType: BleWriteType
    ) {

        val characteristic =
            findCharacteristic(
                characteristicUuid
            )

        val androidWriteType =
            writeType.toAndroidWriteType()

        operationCoordinator.execute(
            timeout = timeoutConfig.writeTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.CharacteristicWritten -> {
                        if (
                            event.characteristicUuid ==
                            characteristicUuid
                        ) {
                            Unit
                        } else {
                            null
                        }
                    }

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.WriteFailed &&
                        error.characteristicUuid ==
                        characteristicUuid.toString()
            },

            starter = {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    bluetoothGatt.writeCharacteristic(
                        characteristic,
                        value,
                        androidWriteType
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType =
                        androidWriteType

                    @Suppress("DEPRECATION")
                    characteristic.value =
                        value

                    @Suppress("DEPRECATION")
                    bluetoothGatt.writeCharacteristic(
                        characteristic
                    )
                }
            }
        )
    }

    override fun observeCharacteristic(
        characteristicUuid: UUID
    ): Flow<ByteArray> {

        return callbackHandler.events
            .filterIsInstance<
                    BleGattEvent.CharacteristicChanged
                    >()
            .filter {
                it.characteristicUuid ==
                        characteristicUuid
            }
            .map {
                it.value
            }
    }

    @SuppressLint("MissingPermission")
    override suspend fun setNotification(
        characteristicUuid: UUID,
        enabled: Boolean
    ) {

        val characteristic =
            findCharacteristic(
                characteristicUuid
            )

        val localEnabled =
            bluetoothGatt
                .setCharacteristicNotification(
                    characteristic,
                    enabled
                )

        if (!localEnabled) {
            throw BleException(
                BleError.NotificationFailed(
                    characteristicUuid =
                        characteristicUuid.toString(),
                    message =
                        "Unable to configure local notification state"
                )
            )
        }

        val descriptor =
            characteristic.getDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG_UUID
            )
                ?: throw BleException(
                    BleError.NotificationFailed(
                        characteristicUuid =
                            characteristicUuid.toString(),
                        message =
                            "Client Characteristic Configuration descriptor was not found"
                    )
                )

        val descriptorValue =
            when {

                !enabled ->
                    BluetoothGattDescriptor
                        .DISABLE_NOTIFICATION_VALUE

                characteristic.properties and
                        BluetoothGattCharacteristic
                            .PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor
                        .ENABLE_INDICATION_VALUE

                else ->
                    BluetoothGattDescriptor
                        .ENABLE_NOTIFICATION_VALUE
            }

        writeDescriptorInternal(
            descriptor = descriptor,
            value = descriptorValue
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun readDescriptor(
        descriptorUuid: UUID
    ): ByteArray {

        val descriptor =
            findDescriptor(
                descriptorUuid
            )

        return operationCoordinator.execute(
            timeout = timeoutConfig.descriptorTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.DescriptorRead -> {
                        if (
                            event.descriptorUuid ==
                            descriptorUuid
                        ) {
                            event.value
                        } else {
                            null
                        }
                    }

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.Unknown &&
                        error.message?.contains(
                            descriptorUuid.toString()
                        ) == true
            },

            starter = {
                bluetoothGatt.readDescriptor(
                    descriptor
                )
            }
        )
    }

    override suspend fun writeDescriptor(
        descriptorUuid: UUID,
        value: ByteArray
    ) {

        val descriptor =
            findDescriptor(
                descriptorUuid
            )

        writeDescriptorInternal(
            descriptor = descriptor,
            value = value
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeDescriptorInternal(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ) {

        operationCoordinator.execute(
            timeout = timeoutConfig.descriptorTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.DescriptorWritten -> {
                        if (
                            event.descriptorUuid ==
                            descriptor.uuid
                        ) {
                            Unit
                        } else {
                            null
                        }
                    }

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.Unknown &&
                        error.message?.contains(
                            descriptor.uuid.toString()
                        ) == true
            },

            starter = {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    bluetoothGatt.writeDescriptor(
                        descriptor,
                        value
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value =
                        value

                    @Suppress("DEPRECATION")
                    bluetoothGatt.writeDescriptor(
                        descriptor
                    )
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun requestMtu(
        mtu: Int
    ): Int {

        require(mtu in MIN_MTU..MAX_MTU) {
            "MTU must be between $MIN_MTU and $MAX_MTU"
        }

        return operationCoordinator.execute(
            timeout = timeoutConfig.mtuTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.MtuChanged ->
                        event.mtu

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.MtuRequestFailed
            },

            starter = {
                bluetoothGatt.requestMtu(mtu)
            }
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun readRssi(): Int {

        return operationCoordinator.execute(
            timeout = timeoutConfig.rssiTimeout,

            matcher = { event ->
                when (event) {
                    is BleGattEvent.RssiRead ->
                        event.rssi

                    else ->
                        null
                }
            },

            errorMatcher = { error ->
                error is BleError.Unknown &&
                        error.message?.contains(
                            "RSSI",
                            ignoreCase = true
                        ) == true
            },

            starter = {
                bluetoothGatt.readRemoteRssi()
            }
        )
    }

    private fun findCharacteristic(
        uuid: UUID
    ): BluetoothGattCharacteristic {

        bluetoothGatt.services.forEach { service ->

            service.characteristics
                .firstOrNull {
                    it.uuid == uuid
                }
                ?.let {
                    return it
                }
        }

        throw BleException(
            BleError.CharacteristicNotFound(
                characteristicUuid =
                    uuid.toString()
            )
        )
    }

    private fun findDescriptor(
        uuid: UUID
    ): BluetoothGattDescriptor {

        bluetoothGatt.services.forEach { service ->

            service.characteristics.forEach {
                    characteristic ->

                characteristic.descriptors
                    .firstOrNull {
                        it.uuid == uuid
                    }
                    ?.let {
                        return it
                    }
            }
        }

        throw BleException(
            BleError.Unknown(
                message =
                    "GATT descriptor not found: $uuid"
            )
        )
    }




    private fun BleWriteType
            .toAndroidWriteType(): Int {

        return when (this) {

            BleWriteType.WITH_RESPONSE ->
                BluetoothGattCharacteristic
                    .WRITE_TYPE_DEFAULT

            BleWriteType.WITHOUT_RESPONSE ->
                BluetoothGattCharacteristic
                    .WRITE_TYPE_NO_RESPONSE

            BleWriteType.SIGNED ->
                BluetoothGattCharacteristic
                    .WRITE_TYPE_SIGNED
        }
    }

    private companion object {

        const val MIN_MTU =
            23

        const val MAX_MTU =
            517

        val CLIENT_CHARACTERISTIC_CONFIG_UUID:
                UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )
    }
}
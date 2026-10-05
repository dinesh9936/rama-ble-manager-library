package com.rama.blecore.error

sealed class BleError(
    open val message: String? = null,
    open val cause: Throwable? = null
) {

    data class BluetoothUnavailable(
        override val message: String =
            "Bluetooth is not available on this device"
    ) : BleError(message)

    data class BluetoothDisabled(
        override val message: String =
            "Bluetooth is disabled"
    ) : BleError(message)

    data class PermissionDenied(
        val permissions: List<String>,
        override val message: String =
            "Required Bluetooth permission is missing"
    ) : BleError(message)

    data class ScanFailed(
        val errorCode: Int? = null,
        override val message: String =
            "BLE scan failed",
        override val cause: Throwable? = null
    ) : BleError(
        message = message,
        cause = cause
    )

    data class ConnectionFailed(
        val deviceAddress: String? = null,
        val status: Int? = null,
        override val message: String =
            "BLE connection failed",
        override val cause: Throwable? = null
    ) : BleError(
        message = message,
        cause = cause
    )

    data class Disconnected(
        val deviceAddress: String? = null,
        val status: Int? = null,
        override val message: String =
            "BLE device disconnected"
    ) : BleError(message)

    data class ServiceDiscoveryFailed(
        val status: Int? = null,
        override val message: String =
            "GATT service discovery failed"
    ) : BleError(message)

    data class ServiceNotFound(
        val serviceUuid: String,
        override val message: String =
            "GATT service not found"
    ) : BleError(message)

    data class CharacteristicNotFound(
        val serviceUuid: String? = null,
        val characteristicUuid: String,
        override val message: String =
            "GATT characteristic not found"
    ) : BleError(message)

    data class ReadFailed(
        val characteristicUuid: String,
        val status: Int? = null,
        override val message: String =
            "Characteristic read failed"
    ) : BleError(message)

    data class WriteFailed(
        val characteristicUuid: String,
        val status: Int? = null,
        override val message: String =
            "Characteristic write failed"
    ) : BleError(message)

    data class NotificationFailed(
        val characteristicUuid: String,
        val status: Int? = null,
        override val message: String =
            "Failed to configure characteristic notifications"
    ) : BleError(message)

    data class DescriptorReadFailed(
        val descriptorUuid: String,
        val status: Int? = null,
        override val message: String =
            "Descriptor read failed"
    ) : BleError(message)

    data class DescriptorWriteFailed(
        val descriptorUuid: String,
        val status: Int? = null,
        override val message: String =
            "Descriptor write failed"
    ) : BleError(message)

    data class MtuRequestFailed(
        val requestedMtu: Int,
        val status: Int? = null,
        override val message: String =
            "MTU request failed"
    ) : BleError(message)

    data class RssiReadFailed(
        val status: Int? = null,
        override val message: String =
            "RSSI read failed"
    ) : BleError(message)

    data class Timeout(
        val operation: String,
        override val message: String =
            "BLE operation timed out"
    ) : BleError(message)

    data class Unknown(
        override val message: String =
            "Unknown BLE error",
        override val cause: Throwable? = null
    ) : BleError(
        message = message,
        cause = cause
    )
}
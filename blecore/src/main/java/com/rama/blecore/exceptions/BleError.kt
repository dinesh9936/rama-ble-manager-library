package com.rama.blecore.exceptions

sealed class BleError(message: String): Exception(message) {
    class DeviceDisconnected : BleError("Device disconnected during operation")
    class ServiceNotFound : BleError("Service UUID not found")
    class CharacteristicNotFound : BleError("Characteristic UUID not found")
    class OperationTimeout : BleError("BLE Operation timed out")
    class OperationFailed(reason: String) : BleError("Operation failed: $reason")
}
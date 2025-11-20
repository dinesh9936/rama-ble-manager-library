package com.rama.blecore.exceptions

sealed class BleError(message: String): Exception(message) {
    class DeviceDisconnected : BleError("The device got disconnected unexpectedly.")
    class ServiceNotFound : BleError("The specified service was not found on the device.")
    class CharacteristicNotFound : BleError("The specified characteristic was not found on the device.")
    class OperationTimeout : BleError("The operation timed out.")
    class OperationFailed(error: String) : BleError("The operation failed due to an unknown error. $error")
}
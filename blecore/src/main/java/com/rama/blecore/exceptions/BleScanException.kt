package com.rama.blecore.exceptions

sealed class BleScanError(
    val errorMessage: String,
    val errorCode: Int,
    cause: Throwable? = null
) : Exception(errorMessage, cause) {

    object BluetoothDisabled :
        BleScanError(
            errorMessage = "Bluetooth is disabled",
            errorCode = 1230
        )

    object ScannerNotAvailable :
        BleScanError(
            errorMessage = "Bluetooth LE Scanner not available",
            errorCode = 1231
        )

    object ScanPermissionMissing :
        BleScanError(
            errorMessage = "Scan permission is required for BLE scanning",
            errorCode = 1232
        )

    class InvalidServiceUUID(val uuid: String) :
        BleScanError(
            errorMessage = "Invalid service UUID: $uuid",
            errorCode = 1233
        )

    class InvalidDeviceNamePrefix(val prefix: String) :
        BleScanError(
            errorMessage = "Invalid device name prefix: $prefix",
            errorCode = 1234
        )

    class ScanFailed(val androidError: Int) :
        BleScanError(
            errorMessage = "BLE scan failed. Android code: $androidError",
            errorCode = 1235
        )

    class ScanTimeout(val durationMs: Long) :
        BleScanError(
            errorMessage = "Scan timed out after $durationMs ms",
            errorCode = 1236
        )

    class Unknown(val reason: String) :
        BleScanError(
            errorMessage = "Unknown scan error: $reason",
            errorCode = 1237
        )
}

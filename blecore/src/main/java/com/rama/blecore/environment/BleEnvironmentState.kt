package com.rama.blecore.environment

data class BleEnvironmentState(
    val bleSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val requiredPermissions: List<String>,
    val missingPermissions: List<String>,
    val locationServiceRequired: Boolean,
    val locationServiceEnabled: Boolean
) {

    val isReady: Boolean
        get() =
            bleSupported &&
                    bluetoothEnabled &&
                    missingPermissions.isEmpty() &&
                    (!locationServiceRequired || locationServiceEnabled)

    val requirements: List<BleRequirement>
        get() {
            val result = mutableListOf<BleRequirement>()

            if (!bleSupported) {
                result += BleRequirement.BleNotSupported
            }

            if (!bluetoothEnabled) {
                result += BleRequirement.BluetoothDisabled
            }

            if (missingPermissions.isNotEmpty()) {
                result += BleRequirement.MissingPermissions(
                    permissions = missingPermissions
                )
            }

            if (
                locationServiceRequired &&
                !locationServiceEnabled
            ) {
                result += BleRequirement.LocationServiceDisabled
            }

            return result
        }
}
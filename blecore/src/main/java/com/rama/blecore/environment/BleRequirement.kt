package com.rama.blecore.environment

sealed interface BleRequirement{
    data object BleNotSupported : BleRequirement

    data object BluetoothDisabled: BleRequirement

    data class MissingPermissions(
        val permissions: List<String>
    ): BleRequirement

    data object LocationServiceDisabled : BleRequirement

}
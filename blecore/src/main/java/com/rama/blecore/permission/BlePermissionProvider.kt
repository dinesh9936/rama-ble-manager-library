package com.rama.blecore.permission

interface BlePermissionProvider {

    fun requiredScanPermissions(): List<String>

    fun requiredConnectPermissions(): List<String>
}
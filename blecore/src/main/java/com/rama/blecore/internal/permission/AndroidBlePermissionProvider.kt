package com.rama.blecore.internal.permission

import android.Manifest
import android.os.Build
import com.rama.blecore.permission.BlePermissionProvider

internal class AndroidBlePermissionProvider :
    BlePermissionProvider {

    override fun requiredScanPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun requiredConnectPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyList()
        }
    }
}
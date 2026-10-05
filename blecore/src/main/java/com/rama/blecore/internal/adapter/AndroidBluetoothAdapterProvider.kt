package com.rama.blecore.internal.adapter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

internal class AndroidBluetoothAdapterProvider(
    context: Context
) {

    private val bluetoothManager: BluetoothManager? =
        context.applicationContext
            .getSystemService(BluetoothManager::class.java)

    fun getAdapter(): BluetoothAdapter? {
        return bluetoothManager?.adapter
    }

    fun isBluetoothAvailable(): Boolean {
        return getAdapter() != null
    }

    fun isBluetoothEnabled(): Boolean {
        return getAdapter()?.isEnabled == true
    }
}
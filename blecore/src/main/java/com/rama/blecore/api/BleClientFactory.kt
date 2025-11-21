package com.rama.blecore.api

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.rama.blecore.internal.BleClientImpl

object BleClientFactory {
    fun create(
        context: Context,
        config: BleConfig = BleConfig()
    ): BleClient{
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth not available")
        return BleClientImpl(
            context = context.applicationContext,
            bluetoothAdapter = adapter,
            config = config
        )

    }
}
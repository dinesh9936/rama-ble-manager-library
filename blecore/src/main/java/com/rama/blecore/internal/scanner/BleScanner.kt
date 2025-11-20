package com.rama.blecore.internal.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow

class BleScanner (
    private val bluetoothAdapter: BluetoothAdapter
){

    fun scanDevices(): Flow<BleDevice> = callbackFlow{
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            close(Throwable("Bluetooth LE Scanner not available"))
            return@callbackFlow
        }


        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf<ScanFilter>()

        val callBack = ScanCallbackHandler{ result->
            trySend(result)
        }

        bluetoothLeScanner.startScan(filters, settings, callBack)

        awaitClose {
            bluetoothLeScanner.stopScan(callBack)
        }
    }


}
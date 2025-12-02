package com.rama.blecore.internal.scanner

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.model.BleDevice

class ScanCallbackHandler(
    private val onDeviceFound: (BleDevice) -> Unit
): ScanCallback() {

    val scannedDevices = mutableListOf<String>()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        result ?: return

        val deviceServiceUUID = result.scanRecord?.serviceUuids
        val device = BleDevice(
            name = result.scanRecord?.deviceName ?: "Unknown",
            address = result.device.address,
            rssi = result.rssi,
            serviceUUID = deviceServiceUUID
        )


        BleLogger.d("Found device: ${device.name} (${device.address}) RSSI=${device.rssi}")
        deviceServiceUUID?.forEach {serviceUUID->
            BleLogger.d("Service UUID is $serviceUUID")
        }



        if (!scannedDevices.contains(device.name)){
            scannedDevices.add(device.name)
            onDeviceFound.invoke(device)
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBatchScanResults(results: List<ScanResult?>?) {
        results?.forEach  { result ->
            result ?: return@forEach
            val deviceServiceUUID = result.scanRecord?.serviceUuids
            val device = BleDevice(
                name = result.device.name ?: "Unknown",
                address = result.device.address,
                rssi = result.rssi,
                serviceUUID = deviceServiceUUID
            )
            onDeviceFound.invoke(device)
        }
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
    }


}
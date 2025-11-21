package com.rama.blecore.internal.scanner

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.annotation.RequiresPermission
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.model.BleDevice

class ScanCallbackHandler(
    private val onDeviceFound: (BleDevice) -> Unit
): ScanCallback() {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        result ?: return
        val device = BleDevice(
            name = result.device.name ?: "Unknown",
            address = result.device.address,
            rssi = result.rssi
        )

        BleLogger.d("Found device: ${device.name} (${device.address}) RSSI=${device.rssi}")

        onDeviceFound.invoke(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBatchScanResults(results: List<ScanResult?>?) {
        results?.forEach  { result ->
            result ?: return@forEach
            val device = BleDevice(
                name = result.device.name ?: "Unknown",
                address = result.device.address,
                rssi = result.rssi
            )
            onDeviceFound.invoke(device)
        }
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
    }


}
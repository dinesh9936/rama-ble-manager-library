package com.rama.blecore.internal.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.rama.blecore.exceptions.BleScanError
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleScanner (
    private val bluetoothAdapter: BluetoothAdapter,
    private val context: Context
){

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scanDevices(
        scanServiceUUID: String = "",
        scanTimeout: Long = 5_000L,
        deviceName: String = "",
        isDeviceNamePrefix: Boolean = false
    ): Flow<BleDevice> = callbackFlow {

        try {
            validateScanRequirements(scanServiceUUID, deviceName)
        } catch (e: BleScanError) {
            close(e)
            return@callbackFlow
        }

        BleLogger.d("Starting BLE scan…")

        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            close(Throwable("Bluetooth LE Scanner not available"))
            return@callbackFlow
        }

        val filters = mutableListOf<ScanFilter>()

        if (scanServiceUUID.isNotEmpty()) {
            try {
                val parcelUuid = ParcelUuid.fromString(scanServiceUUID)
                filters.add(
                    ScanFilter.Builder()
                        .setServiceUuid(parcelUuid)
                        .build()
                )
            } catch (_: Exception) {
                BleLogger.e("Invalid UUID format: $scanServiceUUID")
            }
        }

        if (deviceName.isNotEmpty() && !isDeviceNamePrefix){
            filters.add(
                ScanFilter.Builder()
                    .setDeviceName(deviceName)
                    .build()
            )
        }


        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = ScanCallbackHandler { result ->

            if (deviceName.isNotEmpty() && isDeviceNamePrefix) {
                val name = result.name
                if (!name.startsWith(deviceName, ignoreCase = true)) {
                    return@ScanCallbackHandler
                }
            }
            trySend(result)
        }

        // Start scan
        bluetoothLeScanner.startScan(if (filters.isEmpty()) null else filters, settings, callback)

        // Timeout
        val job = launch {
            delay(scanTimeout)
            BleLogger.d("Scan timeout reached → stopping scan…")
            bluetoothLeScanner.stopScan(callback)
            close()
        }

        // Stop scanning on flow close
        awaitClose {
            job.cancel()
            bluetoothLeScanner.stopScan(callback)
            BleLogger.d("Scan stopped.")
        }
    }

    private fun validateScanRequirements(
        serviceUuid: String,
        deviceNamePrefix: String
    ) {

        if (!bluetoothAdapter.isEnabled) {
            throw BleScanError.BluetoothDisabled
        }

        val permissionCheck = hasScanPermission()
        if (!permissionCheck.first){
            if (permissionCheck.second) throw BleScanError.ScanPermissionMissingAbove12
            else throw BleScanError.ScanPermissionMissingBelow13
        }

        if (serviceUuid.isNotEmpty()) {
            try {
                UUID.fromString(serviceUuid)
            } catch (e: Exception) {
                throw BleScanError.InvalidServiceUUID(serviceUuid)
            }
        }

        if (deviceNamePrefix.isNotEmpty() && deviceNamePrefix.length < 2) {
            throw BleScanError.InvalidDeviceNamePrefix(deviceNamePrefix)
        }

        if (bluetoothAdapter.bluetoothLeScanner == null) {
            throw BleScanError.ScannerNotAvailable
        }
    }


    private fun hasScanPermission(): Pair<Boolean, Boolean> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val isGranted = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
             Pair(isGranted, true)
        } else {
            val isGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            Pair(isGranted, false)
        }
    }

}
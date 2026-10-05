package com.rama.blecore.internal.scan

import android.bluetooth.le.ScanResult
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanResult
import java.util.UUID

internal class ScanResultMapper {

    fun map(
        result: ScanResult
    ): BleScanResult {

        val scanRecord = result.scanRecord

        val serviceUuids =
            scanRecord
                ?.serviceUuids
                ?.map { parcelUuid ->
                    parcelUuid.uuid
                }
                .orEmpty()

        val manufacturerData =
            buildMap {
                val data = scanRecord?.manufacturerSpecificData
                    ?: return@buildMap

                for (index in 0 until data.size()) {
                    put(
                        data.keyAt(index),
                        data.valueAt(index)
                    )
                }
            }

        val serviceData =
            scanRecord
                ?.serviceData
                ?.mapKeys { entry ->
                    entry.key.uuid
                }
                .orEmpty()

        return BleScanResult(
            device = BleDevice(
                address = result.device.address,
                name = scanRecord?.deviceName
            ),
            rssi = result.rssi,
            timestampNanos = result.timestampNanos,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            txPowerLevel = result.txPower.takeIf {
                it != ScanResult.TX_POWER_NOT_PRESENT
            },
            rawAdvertisementData =
                scanRecord?.bytes
        )
    }
}
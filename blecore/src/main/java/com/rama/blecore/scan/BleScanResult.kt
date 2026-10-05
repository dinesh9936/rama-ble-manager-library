package com.rama.blecore.scan

import com.rama.blecore.model.BleDevice
import java.util.UUID

data class BleScanResult(
    val device: BleDevice,
    val rssi: Int,
    val timestampNanos: Long,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val txPowerLevel: Int? = null,
    val rawAdvertisementData: ByteArray? = null
)
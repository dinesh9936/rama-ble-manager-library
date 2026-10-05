package com.rama.blecore.scan

import java.util.UUID

data class BleScanFilter(
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val serviceUuid: UUID? = null,
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null
)
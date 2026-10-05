package com.rama.blecore.gatt

import java.util.UUID

data class BleService(
    val uuid: UUID,
    val characteristics: List<BleCharacteristic> = emptyList()
)
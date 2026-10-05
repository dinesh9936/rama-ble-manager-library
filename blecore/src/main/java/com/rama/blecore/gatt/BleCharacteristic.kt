package com.rama.blecore.gatt

import java.util.UUID

data class BleCharacteristic(
    val uuid: UUID,
    val properties: Set<BleCharacteristicProperty> = emptySet(),
    val descriptors: List<BleDescriptor> = emptyList()
)
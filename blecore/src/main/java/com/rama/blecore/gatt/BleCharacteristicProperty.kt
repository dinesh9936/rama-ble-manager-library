package com.rama.blecore.gatt

enum class BleCharacteristicProperty {
    READ,
    WRITE,
    WRITE_WITHOUT_RESPONSE,
    NOTIFY,
    INDICATE,
    BROADCAST,
    SIGNED_WRITE,
    EXTENDED_PROPERTIES
}
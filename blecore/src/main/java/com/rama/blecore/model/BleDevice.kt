package com.rama.blecore.model

import android.os.ParcelUuid

data class BleDevice(
    var name: String,
    var address: String,
    var rssi: Int,
    var serviceUUID: List<ParcelUuid?>?
)

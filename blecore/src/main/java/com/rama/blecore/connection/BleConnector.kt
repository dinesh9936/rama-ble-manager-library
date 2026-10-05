package com.rama.blecore.connection

import com.rama.blecore.model.BleDevice

interface BleConnector {

    suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig = BleConnectionConfig()
    ): BleConnection
}
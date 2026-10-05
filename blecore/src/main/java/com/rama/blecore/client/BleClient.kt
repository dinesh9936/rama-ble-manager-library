package com.rama.blecore.client

import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.environment.BleEnvironmentChecker
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanResult
import kotlinx.coroutines.flow.Flow

interface BleClient {

    val environment: BleEnvironmentChecker

    fun scan(
        config: BleScanConfig = BleScanConfig()
    ): Flow<BleScanResult>

    suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig = BleConnectionConfig()
    ): BleConnection

    suspend fun stopScan()
}
package com.rama.blecore.internal.client

import com.rama.blecore.client.BleClient
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.connection.BleConnector
import com.rama.blecore.environment.BleEnvironmentChecker
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.scan.BleScanner
import kotlinx.coroutines.flow.Flow

internal class AndroidBleClient(
    override val environment: BleEnvironmentChecker,
    private val scanner: BleScanner,
    private val connector: BleConnector
) : BleClient {

    override fun scan(
        config: BleScanConfig
    ): Flow<BleScanResult> {
        return scanner.scan(config)
    }

    override suspend fun stopScan() {
        scanner.stop()
    }

    override suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection {
        return connector.connect(
            device = device,
            config = config
        )
    }
}
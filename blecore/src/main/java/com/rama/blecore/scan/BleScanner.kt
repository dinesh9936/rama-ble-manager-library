package com.rama.blecore.scan

import kotlinx.coroutines.flow.Flow

interface BleScanner {

    fun scan(
        config: BleScanConfig = BleScanConfig()
    ): Flow<BleScanResult>

    suspend fun stop()
}
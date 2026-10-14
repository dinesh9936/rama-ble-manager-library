package com.rama.blecore.client

import com.rama.blecore.connection.BleConnectionPolicy
import com.rama.blecore.timeout.BleTimeoutConfig

/**
 * Global configuration for a [BleClient].
 *
 * @property timeoutConfig Default timeouts for BLE operations.
 * @property connectionPolicy Controls whether the client
 * supports one or multiple simultaneous device connections.
 */
data class BleClientConfig(
    val timeoutConfig: BleTimeoutConfig = BleTimeoutConfig(),

    val connectionPolicy: BleConnectionPolicy =
        BleConnectionPolicy.MULTI_DEVICE
)
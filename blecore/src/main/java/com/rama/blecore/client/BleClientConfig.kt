package com.rama.blecore.client

import com.rama.blecore.timeout.BleTimeoutConfig


data class BleClientConfig(
    val timeoutConfig: BleTimeoutConfig = BleTimeoutConfig()
)
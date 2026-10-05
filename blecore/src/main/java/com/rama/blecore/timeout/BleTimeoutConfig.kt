package com.rama.blecore.timeout

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class BleTimeoutConfig(
    val scanTimeout: Duration? = null,
    val connectionTimeout: Duration = 15.seconds,
    val serviceDiscoveryTimeout: Duration = 10.seconds,
    val readTimeout: Duration = 10.seconds,
    val writeTimeout: Duration = 10.seconds,
    val descriptorTimeout: Duration = 10.seconds,
    val mtuTimeout: Duration = 10.seconds,
    val rssiTimeout: Duration = 10.seconds
)
package com.rama.blecore.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.rama.blecore.retry.BleRetryPolicy


data class BleConnectionConfig(
    val autoConnect: Boolean = false,
    val connectionTimeout: Duration = 15.seconds,
    val preferredMtu: Int? = null,
    val retryPolicy: BleRetryPolicy = BleRetryPolicy.None
)
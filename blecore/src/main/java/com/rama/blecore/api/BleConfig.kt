package com.rama.blecore.api

data class BleConfig(
    val scanPeriodMillis: Long = 10_000L,
    val autoReconnect: Boolean = false,
    val enableLogging: Boolean = true
)
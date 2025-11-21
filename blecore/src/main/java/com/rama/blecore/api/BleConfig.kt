package com.rama.blecore.api

data class BleConfig(
    val scanPeriodMillis: Long = 10_000L,
    val autoReconnect: Boolean = false,
    val enableLogging: Boolean = true,
    val retryCount: Int = 2,
    val retryDelayMillis: Long = 150L,
    val reconnectAttempts: Int = 3,
    val reconnectDelayMillis: Long = 1000L


)
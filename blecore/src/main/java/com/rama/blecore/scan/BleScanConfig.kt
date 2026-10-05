package com.rama.blecore.scan

data class BleScanConfig(
    val filters: List<BleScanFilter> = emptyList(),
    val scanMode: BleScanMode = BleScanMode.BALANCED,
    val reportDelayMillis: Long = 0L,
    val allowDuplicates: Boolean = true
)
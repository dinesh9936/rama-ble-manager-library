package com.rama.blecore.model

sealed class BleScanState {
    object Started : BleScanState()
    object Scanning : BleScanState()
    object Stopped : BleScanState()
    object Timeout : BleScanState()
    data class DeviceFound(val device: BleDevice) : BleScanState()
    data class Error(val error: Throwable) : BleScanState()
}

package com.rama.blecore.internal.utils

import android.util.Log

object BleLogger {
    private const val TAG = "BLE-Core"

    var enableLogging: Boolean = true

    fun d(msg: String) {
        if (enableLogging) Log.d(TAG, msg)
    }

    fun e(msg: String) {
        if (enableLogging) Log.e(TAG, msg)
    }

    fun w(msg: String) {
        if (enableLogging) Log.w(TAG, msg)
    }
}
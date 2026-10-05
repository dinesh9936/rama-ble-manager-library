package com.rama.blecore.internal.environment

import android.os.Build
import com.rama.blecore.environment.BleEnvironmentPolicy

internal class AndroidBleEnvironmentPolicy :
    BleEnvironmentPolicy {

    override fun isLocationServiceRequired(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    }
}
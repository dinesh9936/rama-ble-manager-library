package com.rama.blecore.internal.permission

import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.rama.blecore.permission.BlePermissionChecker

internal class AndroidBlePermissionChecker(
    context: Context
) : BlePermissionChecker {

    private val appContext =
        context.applicationContext

    override fun isGranted(
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
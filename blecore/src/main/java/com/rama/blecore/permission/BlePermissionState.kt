package com.rama.blecore.permission

data class BlePermissionState(
    val requiredPermissions: List<String>,
    val grantedPermissions: List<String>,
    val missingPermissions: List<String>
) {

    val allGranted: Boolean
        get() = missingPermissions.isEmpty()
}
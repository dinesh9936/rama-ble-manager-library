package com.rama.blecore.permission

interface BlePermissionChecker {

    fun isGranted(permission: String): Boolean

    fun areGranted(permissions: Collection<String>): Boolean {
        return permissions.all(::isGranted)
    }

    fun missingPermissions(
        permissions: Collection<String>
    ): List<String> {
        return permissions.filterNot(::isGranted)
    }
}
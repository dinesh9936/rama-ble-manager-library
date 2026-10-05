package com.rama.blecore.internal.environment

import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.rama.blecore.environment.BleEnvironmentChecker
import com.rama.blecore.environment.BleEnvironmentPolicy
import com.rama.blecore.environment.BleEnvironmentState
import com.rama.blecore.internal.adapter.AndroidBluetoothAdapterProvider
import com.rama.blecore.permission.BlePermissionChecker
import com.rama.blecore.permission.BlePermissionProvider

internal class AndroidBleEnvironmentChecker(
    context: Context,
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val permissionProvider: BlePermissionProvider,
    private val permissionChecker: BlePermissionChecker,
    private val environmentPolicy: BleEnvironmentPolicy
) : BleEnvironmentChecker {

    private val appContext =
        context.applicationContext

    override fun check(): BleEnvironmentState {

        val requiredPermissions =
            buildRequiredPermissions()

        val missingPermissions =
            permissionChecker.missingPermissions(
                requiredPermissions
            )

        val locationRequired =
            environmentPolicy.isLocationServiceRequired()

        return BleEnvironmentState(
            bleSupported =
                adapterProvider.isBluetoothAvailable(),

            bluetoothEnabled =
                adapterProvider.isBluetoothEnabled(),

            requiredPermissions =
                requiredPermissions,

            missingPermissions =
                missingPermissions,

            locationServiceRequired =
                locationRequired,

            locationServiceEnabled =
                !locationRequired ||
                        isLocationServiceEnabled()
        )
    }

    private fun buildRequiredPermissions(): List<String> {
        return (
                permissionProvider.requiredScanPermissions() +
                        permissionProvider.requiredConnectPermissions()
                )
            .distinct()
    }


    private fun isLocationServiceEnabled(): Boolean {
        val locationManager =
            appContext.getSystemService(
                Context.LOCATION_SERVICE
            ) as? LocationManager
                ?: return false

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            ) ||
                    locationManager.isProviderEnabled(
                        LocationManager.NETWORK_PROVIDER
                    )
        }
    }
}
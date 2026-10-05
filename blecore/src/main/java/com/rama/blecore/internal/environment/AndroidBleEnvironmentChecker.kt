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

    /**
     * Checks only the requirements needed for BLE scanning.
     *
     * Android 12+:
     * - BLUETOOTH_SCAN
     *
     * Android 11 and below:
     * - Location permission
     * - Location service enabled when required
     */
    override fun checkScan(): BleEnvironmentState {
        val requiredPermissions =
            permissionProvider
                .requiredScanPermissions()
                .distinct()

        val locationRequired =
            environmentPolicy
                .isLocationServiceRequired()

        return buildEnvironmentState(
            requiredPermissions = requiredPermissions,
            locationServiceRequired = locationRequired
        )
    }

    /**
     * Checks only the requirements needed for connecting
     * to a BLE device.
     *
     * Android 12+:
     * - BLUETOOTH_CONNECT
     *
     * Android 11 and below:
     * - No runtime Bluetooth connect permission
     */
    override fun checkConnect(): BleEnvironmentState {
        val requiredPermissions =
            permissionProvider
                .requiredConnectPermissions()
                .distinct()

        return buildEnvironmentState(
            requiredPermissions = requiredPermissions,
            locationServiceRequired = false
        )
    }

    /**
     * Common environment state builder.
     */
    private fun buildEnvironmentState(
        requiredPermissions: List<String>,
        locationServiceRequired: Boolean
    ): BleEnvironmentState {

        val missingPermissions =
            permissionChecker.missingPermissions(
                requiredPermissions
            )

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
                locationServiceRequired,

            locationServiceEnabled =
                !locationServiceRequired ||
                        isLocationServiceEnabled()
        )
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
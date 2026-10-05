package com.rama.blecore.client

import android.content.Context
import com.rama.blecore.environment.BleEnvironmentPolicy
import com.rama.blecore.internal.adapter.AndroidBluetoothAdapterProvider
import com.rama.blecore.internal.client.AndroidBleClient
import com.rama.blecore.internal.connection.AndroidBleConnector
import com.rama.blecore.internal.environment.AndroidBleEnvironmentChecker
import com.rama.blecore.internal.environment.AndroidBleEnvironmentPolicy
import com.rama.blecore.internal.permission.AndroidBlePermissionChecker
import com.rama.blecore.internal.permission.AndroidBlePermissionProvider
import com.rama.blecore.internal.scan.AndroidBleScanner
import com.rama.blecore.internal.scan.ScanResultMapper
import com.rama.blecore.permission.BlePermissionProvider

object BleClientFactory {

    fun create(
        context: Context,
        config: BleClientConfig = BleClientConfig(),
        permissionProvider: BlePermissionProvider? = null,
        environmentPolicy: BleEnvironmentPolicy? = null
    ): BleClient {

        val appContext =
            context.applicationContext

        val actualPermissionProvider =
            permissionProvider
                ?: AndroidBlePermissionProvider()

        val actualEnvironmentPolicy =
            environmentPolicy
                ?: AndroidBleEnvironmentPolicy()

        val adapterProvider =
            AndroidBluetoothAdapterProvider(
                context = appContext
            )

        val permissionChecker =
            AndroidBlePermissionChecker(
                context = appContext
            )

        val environmentChecker =
            AndroidBleEnvironmentChecker(
                context = appContext,
                adapterProvider = adapterProvider,
                permissionProvider = actualPermissionProvider,
                permissionChecker = permissionChecker,
                environmentPolicy = actualEnvironmentPolicy
            )

        val scanner =
            AndroidBleScanner(
                adapterProvider = adapterProvider,
                scanResultMapper = ScanResultMapper(),
                timeoutConfig = config.timeoutConfig
            )

        val connector =
            AndroidBleConnector(
                context = appContext,
                adapterProvider = adapterProvider,
                timeoutConfig = config.timeoutConfig
            )

        return AndroidBleClient(
            environment = environmentChecker,
            scanner = scanner,
            connector = connector
        )
    }
}
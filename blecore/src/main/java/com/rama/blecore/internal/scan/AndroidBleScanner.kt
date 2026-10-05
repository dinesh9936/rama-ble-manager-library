package com.rama.blecore.internal.scan

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.internal.adapter.AndroidBluetoothAdapterProvider
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanMode
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.scan.BleScanner
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal class AndroidBleScanner(
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val scanResultMapper: ScanResultMapper,
    private val timeoutConfig: BleTimeoutConfig
) : BleScanner {

    private val lock = Any()

    private var activeScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    override fun scan(
        config: BleScanConfig
    ): Flow<BleScanResult> = callbackFlow {

        val adapter =
            adapterProvider.getAdapter()

        if (adapter == null) {
            close(
                BleException(
                    BleError.BluetoothUnavailable()
                )
            )
            return@callbackFlow
        }

        if (!adapter.isEnabled) {
            close(
                BleException(
                    BleError.BluetoothDisabled()
                )
            )
            return@callbackFlow
        }

        val scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {
            close(
                BleException(
                    BleError.ScanFailed(
                        message =
                            "Bluetooth LE scanner is unavailable"
                    )
                )
            )
            return@callbackFlow
        }

        val seenDevices =
            mutableSetOf<String>()

        val callback =
            object : ScanCallback() {

                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult
                ) {
                    emitResult(
                        result = result,
                        config = config,
                        seenDevices = seenDevices
                    )
                }

                override fun onBatchScanResults(
                    results: MutableList<ScanResult>
                ) {
                    results.forEach { result ->
                        emitResult(
                            result = result,
                            config = config,
                            seenDevices = seenDevices
                        )
                    }
                }

                override fun onScanFailed(
                    errorCode: Int
                ) {
                    synchronized(lock) {
                        if (
                            activeScanCallback === this
                        ) {
                            activeScanCallback = null
                        }
                    }

                    close(
                        BleException(
                            BleError.ScanFailed(
                                errorCode = errorCode,
                                message =
                                    scanErrorMessage(
                                        errorCode
                                    )
                            )
                        )
                    )
                }

                private fun emitResult(
                    result: ScanResult,
                    config: BleScanConfig,
                    seenDevices: MutableSet<String>
                ) {
                    val address =
                        result.device.address

                    if (!config.allowDuplicates) {
                        val isNew =
                            seenDevices.add(address)

                        if (!isNew) {
                            return
                        }
                    }

                    trySend(
                        scanResultMapper.map(result)
                    )
                }
            }

        synchronized(lock) {

            activeScanCallback
                ?.let { previousCallback ->

                    runCatching {
                        scanner.stopScan(
                            previousCallback
                        )
                    }
                }

            activeScanCallback =
                callback
        }

        try {

            scanner.startScan(
                config.filters.map(
                    ::toAndroidScanFilter
                ),
                buildScanSettings(config),
                callback
            )

        } catch (
            securityException: SecurityException
        ) {

            synchronized(lock) {
                if (
                    activeScanCallback ===
                    callback
                ) {
                    activeScanCallback = null
                }
            }

            close(
                BleException(
                    BleError.PermissionDenied(
                        permissions =
                            emptyList(),
                        message =
                            "Required BLE scan permission is missing"
                    )
                )
            )

            return@callbackFlow

        } catch (throwable: Throwable) {

            synchronized(lock) {
                if (
                    activeScanCallback ===
                    callback
                ) {
                    activeScanCallback = null
                }
            }

            close(
                BleException(
                    BleError.ScanFailed(
                        message =
                            "Unable to start BLE scan",
                        cause = throwable
                    )
                )
            )

            return@callbackFlow
        }

        val timeoutJob =
            timeoutConfig.scanTimeout
                ?.let { timeout ->

                    launch {

                        delay(timeout)

                        runCatching {
                            scanner.stopScan(
                                callback
                            )
                        }

                        synchronized(lock) {
                            if (
                                activeScanCallback ===
                                callback
                            ) {
                                activeScanCallback =
                                    null
                            }
                        }

                        close()
                    }
                }

        awaitClose {

            timeoutJob?.cancel()

            runCatching {
                scanner.stopScan(
                    callback
                )
            }

            synchronized(lock) {
                if (
                    activeScanCallback ===
                    callback
                ) {
                    activeScanCallback =
                        null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {

        val adapter =
            adapterProvider.getAdapter()
                ?: return

        val scanner =
            adapter.bluetoothLeScanner
                ?: return

        val callback =
            synchronized(lock) {
                activeScanCallback
                    .also {
                        activeScanCallback =
                            null
                    }
            }
                ?: return

        try {
            scanner.stopScan(callback)
        } catch (
            _: SecurityException
        ) {
            // Permission may have been revoked
            // while scan was active.
        }
    }

    private fun buildScanSettings(
        config: BleScanConfig
    ): ScanSettings {

        return ScanSettings.Builder()
            .setScanMode(
                config.scanMode
                    .toAndroidScanMode()
            )
            .setReportDelay(
                config.reportDelayMillis
            )
            .build()
    }

    private fun toAndroidScanFilter(
        filter:
        com.rama.blecore.scan.BleScanFilter
    ): ScanFilter {

        val builder =
            ScanFilter.Builder()

        filter.deviceName
            ?.let(builder::setDeviceName)

        filter.deviceAddress
            ?.let(builder::setDeviceAddress)

        filter.serviceUuid
            ?.let {
                builder.setServiceUuid(
                    ParcelUuid(it)
                )
            }

        filter.manufacturerId
            ?.let { manufacturerId ->

                filter.manufacturerData
                    ?.let { data ->

                        builder.setManufacturerData(
                            manufacturerId,
                            data
                        )
                    }
                    ?: builder
                        .setManufacturerData(
                            manufacturerId,
                            byteArrayOf()
                        )
            }

        return builder.build()
    }

    private fun BleScanMode
            .toAndroidScanMode(): Int {

        return when (this) {

            BleScanMode.LOW_POWER ->
                ScanSettings
                    .SCAN_MODE_LOW_POWER

            BleScanMode.BALANCED ->
                ScanSettings
                    .SCAN_MODE_BALANCED

            BleScanMode.LOW_LATENCY ->
                ScanSettings
                    .SCAN_MODE_LOW_LATENCY
        }
    }

    private fun scanErrorMessage(
        errorCode: Int
    ): String {

        return when (errorCode) {

            ScanCallback
                .SCAN_FAILED_ALREADY_STARTED ->
                "BLE scan has already been started"

            ScanCallback
                .SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "BLE scanner application registration failed"

            ScanCallback
                .SCAN_FAILED_INTERNAL_ERROR ->
                "Android Bluetooth stack reported an internal scan error"

            ScanCallback
                .SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "Requested BLE scan feature is not supported"

            ScanCallback
                .SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                "BLE scan failed because hardware resources are unavailable"

            ScanCallback
                .SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                "BLE scans are being started too frequently"

            else ->
                "BLE scan failed with error code $errorCode"
        }
    }
}
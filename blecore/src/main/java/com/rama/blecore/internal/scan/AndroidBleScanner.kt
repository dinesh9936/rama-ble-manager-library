package com.rama.blecore.internal.scan

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.internal.adapter.AndroidBluetoothAdapterProvider
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanMode
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.scan.BleScanner
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
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

    companion object {

        private const val TAG = "RamaBleScanner"

        private const val SCAN_TOO_FREQUENTLY = 6

        private val scanRateLimiter = BleScanRateLimiter(
            maxScans = 4,
            windowMillis = 30_000L
        )
    }

    private val lock = Any()

    private data class ActiveScan(
        val callback: ScanCallback,
        val channel: SendChannel<BleScanResult>
    )

    private var activeScan: ActiveScan? = null

    // --------------------------------------------------
    // START SCANNING
    // --------------------------------------------------

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override fun scan(
        config: BleScanConfig
    ): Flow<BleScanResult> = callbackFlow {

        Log.d(TAG, "scan: requested")

        // STEP 1: Check Bluetooth adapter

        val adapter = adapterProvider.getAdapter()

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

        // STEP 2: Get Android BLE scanner

        val scanner = adapter.bluetoothLeScanner

        if (scanner == null) {

            close(
                BleException(
                    BleError.ScanFailed(
                        message = "Bluetooth LE scanner is unavailable"
                    )
                )
            )

            return@callbackFlow
        }

        // STEP 3: Prepare scan filters and settings

        val androidFilters: List<ScanFilter>
        val androidSettings: ScanSettings

        try {

            androidFilters = config.filters.map(
                ::toAndroidScanFilter
            )

            androidSettings = buildScanSettings(config)

        } catch (exception: Exception) {

            close(
                BleException(
                    BleError.ScanFailed(
                        message = "Invalid BLE scan configuration",
                        cause = exception
                    )
                )
            )

            return@callbackFlow
        }

        val seenDevices = mutableSetOf<String>()

        // STEP 4: Create scan callback

        val callback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                emitResult(result)
            }

            override fun onBatchScanResults(
                results: MutableList<ScanResult>
            ) {

                results.forEach { result ->
                    emitResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {

                Log.e(
                    TAG,
                    "onScanFailed: errorCode=$errorCode"
                )

                val error = mapScanError(errorCode)

                synchronized(lock) {

                    if (activeScan?.callback === this) {
                        activeScan = null
                    }
                }

                close(BleException(error))
            }

            private fun emitResult(
                result: ScanResult
            ) {

                if (channel.isClosedForSend) {
                    return
                }

                try {

                    val address = result.device.address

                    if (!config.allowDuplicates) {

                        val isNew = synchronized(seenDevices) {
                            seenDevices.add(address)
                        }

                        if (!isNew) {
                            return
                        }
                    }

                    val mappedResult = scanResultMapper.map(result)

                    trySend(mappedResult)

                } catch (exception: Exception) {

                    Log.e(
                        TAG,
                        "emitResult: failed to map scan result",
                        exception
                    )
                }
            }
        }

        // STEP 5: Register and start scan atomically

        val started = synchronized(lock) {

            if (activeScan != null) {

                Log.w(
                    TAG,
                    "scan: another scan is already active"
                )

                close(
                    BleException(
                        BleError.ScanFailed(
                            errorCode = ScanCallback.SCAN_FAILED_ALREADY_STARTED,
                            message = "A BLE scan is already running"
                        )
                    )
                )

                false

            } else {

                val retryAfterMs = scanRateLimiter.tryAcquire()

                if (retryAfterMs != null) {

                    Log.w(
                        TAG,
                        "scan: rate limited, retryAfterMs=$retryAfterMs"
                    )

                    close(
                        BleException(
                            BleError.ScanTooFrequently(
                                retryAfterMs = retryAfterMs
                            )
                        )
                    )

                    false

                } else {

                    activeScan = ActiveScan(
                        callback = callback,
                        channel = channel
                    )

                    try {

                        scanner.startScan(
                            androidFilters,
                            androidSettings,
                            callback
                        )

                        Log.d(
                            TAG,
                            "scan: Android BLE scan requested"
                        )

                        true

                    } catch (exception: SecurityException) {

                        activeScan = null

                        Log.e(
                            TAG,
                            "scan: Bluetooth permission denied",
                            exception
                        )

                        close(
                            BleException(
                                BleError.PermissionDenied(
                                    permissions = emptyList(),
                                    message = "Required BLE scan permission is missing"
                                )
                            )
                        )

                        false

                    } catch (exception: Exception) {

                        activeScan = null

                        Log.e(
                            TAG,
                            "scan: failed to start",
                            exception
                        )

                        close(
                            BleException(
                                BleError.ScanFailed(
                                    message = "Unable to start BLE scan",
                                    cause = exception
                                )
                            )
                        )

                        false
                    }
                }
            }
        }

        if (!started) {
            return@callbackFlow
        }

        // STEP 6: Optional scan timeout

        val timeoutJob = timeoutConfig.scanTimeout?.let { timeout ->

            launch {

                delay(timeout)

                Log.d(
                    TAG,
                    "scan: timeout reached"
                )

                // Closing the channel triggers awaitClose,
                // which stops the Android scanner.
                close()
            }
        }

        // STEP 7: Cleanup on cancellation,
        // timeout, error, or manual stop.

        awaitClose {

            timeoutJob?.cancel()

            synchronized(lock) {

                if (activeScan?.callback === callback) {
                    activeScan = null
                }

                // Stop only this collector's callback.
                // A newer scan will have a different callback.
                try {

                    scanner.stopScan(callback)

                    Log.d(
                        TAG,
                        "scan: Android BLE scan stopped"
                    )

                } catch (exception: SecurityException) {

                    Log.w(
                        TAG,
                        "scan: permission missing during cleanup",
                        exception
                    )

                } catch (exception: Exception) {

                    Log.e(
                        TAG,
                        "scan: cleanup failed",
                        exception
                    )
                }
            }

            Log.d(
                TAG,
                "scan: Flow closed and resources released"
            )
        }
    }

    // --------------------------------------------------
    // STOP SCANNING
    // --------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun stop() {

        Log.d(TAG, "stop: requested")

        val session = synchronized(lock) {

            activeScan.also {
                activeScan = null
            }
        } ?: run {

            Log.d(TAG, "stop: no active scan")

            return
        }

        // Stop Android scanning immediately.

        val scanner = runCatching {
            adapterProvider.getAdapter()?.bluetoothLeScanner
        }.getOrNull()

        try {

            scanner?.stopScan(session.callback)

        } catch (exception: SecurityException) {

            Log.w(
                TAG,
                "stop: permission denied",
                exception
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "stop: failed",
                exception
            )
        }

        // Complete the Flow of the active scan.
        // awaitClose handles final cleanup.

        session.channel.close()

        Log.d(TAG, "stop: completed")
    }

    // --------------------------------------------------
    // SCAN ERROR MAPPING
    // --------------------------------------------------

    private fun mapScanError(
        errorCode: Int
    ): BleError {

        return when (errorCode) {

            SCAN_TOO_FREQUENTLY -> {

                BleError.ScanTooFrequently(
                    message = "BLE scanning is being started too frequently"
                )
            }

            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "BLE scan has already been started"
                )
            }

            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "BLE scanner application registration failed"
                )
            }

            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "Android Bluetooth stack reported an internal scan error"
                )
            }

            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "Requested BLE scan feature is not supported"
                )
            }

            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "BLE hardware resources are unavailable"
                )
            }

            else -> {

                BleError.ScanFailed(
                    errorCode = errorCode,
                    message = "BLE scan failed with error code $errorCode"
                )
            }
        }
    }

    // --------------------------------------------------
    // SCAN SETTINGS
    // --------------------------------------------------

    private fun buildScanSettings(
        config: BleScanConfig
    ): ScanSettings {

        return ScanSettings.Builder()
            .setScanMode(
                config.scanMode.toAndroidScanMode()
            )
            .setReportDelay(
                config.reportDelayMillis
            )
            .build()
    }

    // --------------------------------------------------
    // SCAN FILTERS
    // --------------------------------------------------

    private fun toAndroidScanFilter(
        filter: com.rama.blecore.scan.BleScanFilter
    ): ScanFilter {

        val builder = ScanFilter.Builder()

        filter.deviceName?.let {
            builder.setDeviceName(it)
        }

        filter.deviceAddress?.let {
            builder.setDeviceAddress(it)
        }

        filter.serviceUuid?.let {
            builder.setServiceUuid(ParcelUuid(it))
        }

        filter.manufacturerId?.let { manufacturerId ->

            val data = filter.manufacturerData

            builder.setManufacturerData(
                manufacturerId,
                data ?: byteArrayOf()
            )
        }

        return builder.build()
    }

    // --------------------------------------------------
    // SCAN MODE
    // --------------------------------------------------

    private fun BleScanMode.toAndroidScanMode(): Int {

        return when (this) {

            BleScanMode.LOW_POWER ->
                ScanSettings.SCAN_MODE_LOW_POWER

            BleScanMode.BALANCED ->
                ScanSettings.SCAN_MODE_BALANCED

            BleScanMode.LOW_LATENCY ->
                ScanSettings.SCAN_MODE_LOW_LATENCY
        }
    }
}
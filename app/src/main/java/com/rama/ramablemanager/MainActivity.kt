package com.rama.ramablemanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.rama.blecore.client.BleClientFactory
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val bleClient by lazy {
        BleClientFactory.create(
            context = this
        )
    }

    private val scanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted = permissions.values.all { granted -> granted }

            if (allGranted) {
                Log.d(TAG, "Scan permissions granted.")
                startBleScan()
            } else {
                Log.d(TAG, "Scan permission denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndStartScan()
    }

    private fun checkAndStartScan() {

        val environment =
            bleClient.environment.checkScan()

        if (!environment.bleSupported) {
            Log.d(TAG, "BLE not supported on this device.")
            return
        }

        Log.d(TAG, "BLE supported.")

        if (!environment.bluetoothEnabled) {
            Log.d(TAG, "Bluetooth is disabled.")
            return
        }

        Log.d(TAG, "Bluetooth enabled.")

        if (environment.missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${environment.missingPermissions}")
            scanPermissionLauncher.launch(environment.missingPermissions.toTypedArray())
            return
        }

        Log.d(TAG, "All scan permissions granted.")

        startBleScan()
    }

    private fun startBleScan() {

        val bleScanConfig =
            BleScanConfig(
                allowDuplicates = false,

            )

        lifecycleScope.launch {

            bleClient
                .scan(
                    config = bleScanConfig
                )
                .catch { throwable ->

                    Log.e(
                        TAG,
                        "Scan failed",
                        throwable
                    )
                }
                .collect { result ->

                    Log.d(
                        TAG,
                        "Device name: ${result.device.name}"
                    )

                    Log.d(
                        TAG,
                        "Device address: ${result.device.address}"
                    )
                }
            delay(5.seconds)
            bleClient.stopScan()
        }
    }
}

private const val TAG = "RamaBleSample"
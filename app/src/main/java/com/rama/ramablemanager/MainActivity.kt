package com.rama.ramablemanager

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rama.blecore.client.BleClientConfig
import com.rama.blecore.client.BleClientFactory
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val bleTimeoutConfig =
        BleTimeoutConfig(
            scanTimeout = 2.seconds
        )

    private val bleClient by lazy {
        BleClientFactory.create(
            context = this,
            config = BleClientConfig(
                timeoutConfig = bleTimeoutConfig
            )
        )
    }

    private val scannedDevices =
        mutableStateListOf<BleScanResult>()

    private var isScanning by mutableStateOf(false)

    private var scanMessage by mutableStateOf(
        "Press Scan to discover BLE devices."
    )

    private var scanJob: Job? = null

    private val scanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                permissions.values.all { granted ->
                    granted
                }

            if (allGranted) {

                Log.d(
                    TAG,
                    "Scan permissions granted."
                )

                startBleScan()

            } else {

                Log.d(
                    TAG,
                    "Scan permission denied."
                )

                scanMessage =
                    "BLE scan permission denied."
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme {

                BleScannerScreen(
                    devices = scannedDevices,
                    isScanning = isScanning,
                    message = scanMessage,
                    onScanClick = {
                        checkAndStartScan()
                    },
                    onStopClick = {
                        stopBleScan()
                    }
                )
            }
        }
    }

    private fun checkAndStartScan() {

        val environment =
            bleClient.environment.checkScan()

        if (!environment.bleSupported) {

            Log.d(
                TAG,
                "BLE not supported on this device."
            )

            scanMessage =
                "BLE is not supported on this device."

            return
        }

        Log.d(
            TAG,
            "BLE supported."
        )

        if (!environment.bluetoothEnabled) {

            Log.d(
                TAG,
                "Bluetooth is disabled."
            )

            scanMessage =
                "Please enable Bluetooth."

            return
        }

        Log.d(
            TAG,
            "Bluetooth enabled."
        )

        if (
            environment
                .missingPermissions
                .isNotEmpty()
        ) {

            Log.d(
                TAG,
                "Requesting permissions: " +
                        environment.missingPermissions
            )

            scanPermissionLauncher.launch(
                environment
                    .missingPermissions
                    .toTypedArray()
            )

            return
        }

        Log.d(
            TAG,
            "All scan permissions granted."
        )

        startBleScan()
    }

    private fun startBleScan() {

        // Cancel previous collection if user scans again.
        scanJob?.cancel()

        scannedDevices.clear()

        isScanning = true

        scanMessage =
            "Scanning for BLE devices..."

        val bleScanConfig =
            BleScanConfig(
                allowDuplicates = false
            )

        scanJob =
            lifecycleScope.launch {

                bleClient
                    .scan(
                        config = bleScanConfig
                    )
                    .catch { throwable ->

                        when (throwable) {

                            is BleException -> {
                                when (val error = throwable.error) {

                                    is BleError.ScanTooFrequently -> {
                                        Log.e(
                                            TAG,
                                            "Scan limited. Retry after " +
                                                    "${error.retryAfterMs}ms"
                                        )
                                        Toast.makeText(this@MainActivity, "Scan limited. Retry after " +
                                                "${error.retryAfterMs}ms", Toast.LENGTH_LONG).show()
                                    }

                                    is BleError.ScanFailed -> {
                                        Log.e(
                                            TAG,
                                            "Scan failed: ${error.message}"
                                        )
                                    }

                                    else -> {
                                        Log.e(
                                            TAG,
                                            "BLE error: ${error.message}"
                                        )
                                    }
                                }
                            }

                            else -> {
                                Log.e(TAG, "Unexpected error", throwable)
                            }
                        }
                    }
                    .onCompletion {

                        Log.d(
                            TAG,
                            "BLE scan completed."
                        )

                        isScanning = false

                        scanMessage =
                            if (scannedDevices.isEmpty()) {
                                "No BLE devices found."
                            } else {
                                "${scannedDevices.size} devices found."
                            }
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

                        /*
                         * Extra protection against duplicate
                         * addresses at UI level.
                         */
                        val alreadyExists =
                            scannedDevices.any {
                                it.device.address ==
                                        result.device.address
                            }

                        if (!alreadyExists) {
                            scannedDevices.add(result)
                        }
                    }
            }
    }

    private fun stopBleScan() {

        lifecycleScope.launch {

            bleClient.stopScan()

            scanJob?.cancel()
            scanJob = null

            isScanning = false

            scanMessage =
                "Scan stopped."
        }
    }
}

@Composable
private fun BleScannerScreen(
    devices: List<BleScanResult>,
    isScanning: Boolean,
    message: String,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit
) {

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
        ) {

            Text(
                text = "Rama BLE Manager",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Button(
                    onClick = onScanClick,
                    enabled = !isScanning
                ) {

                    Text(
                        text =
                            if (isScanning) {
                                "Scanning..."
                            } else {
                                "Scan BLE Devices"
                            }
                    )
                }

                if (isScanning) {

                    Button(
                        onClick = onStopClick
                    ) {
                        Text("Stop")
                    }

                    CircularProgressIndicator()
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text = message,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Devices (${devices.size})",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                items(
                    items = devices,
                    key = {
                        it.device.address
                    }
                ) { result ->

                    BleDeviceItem(
                        result = result
                    )
                }
            }
        }
    }
}

@Composable
private fun BleDeviceItem(
    result: BleScanResult
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp)
        ) {

            Text(
                text =
                    result.device.name
                        ?: "Unknown device",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "Address: ${result.device.address}",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "RSSI: ${result.rssi} dBm",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }
    }
}

private const val TAG =
    "RamaBleSample"
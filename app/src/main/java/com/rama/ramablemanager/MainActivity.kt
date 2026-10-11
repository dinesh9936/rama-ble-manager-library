package com.rama.ramablemanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rama.blecore.client.BleClientConfig
import com.rama.blecore.client.BleClientFactory
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val bleClient by lazy {
        BleClientFactory.create(
            context = applicationContext,
            config = BleClientConfig(
                timeoutConfig = BleTimeoutConfig(scanTimeout = 2.seconds)
            )
        )
    }

    private val scannedDevices = mutableStateListOf<BleScanResult>()
    private var isScanning by mutableStateOf(false)
    private var scanMessage by mutableStateOf("Press Scan to discover BLE devices.")
    private var selectedDevice by mutableStateOf<BleDevice?>(null)
    private var attemptCount by mutableStateOf("3")
    private var isRunning by mutableStateOf(false)
    private var testMessage by mutableStateOf("Enter a count, then run the test.")
    private val testResults = mutableStateListOf<String>()

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var activeConnection: BleConnection? = null
    private var pendingAttemptCount: Int? = null

    private val scanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startBleScan()
        else scanMessage = "BLE scan permission denied."
    }

    private val connectPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val count = pendingAttemptCount
        pendingAttemptCount = null
        if (permissions.values.all { it } && count != null) {
            startConnectionTest(count)
        } else {
            testMessage = "BLE connection permission denied."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val selected = selectedDevice
                BackHandler(enabled = selected != null && !isRunning && activeConnection == null) {
                    selectedDevice = null
                }
                if (selected == null) {
                    ScannerScreen(
                        devices = scannedDevices,
                        isScanning = isScanning,
                        message = scanMessage,
                        onScanClick = ::checkAndStartScan,
                        onStopClick = ::stopBleScan,
                        onDeviceClick = { device ->
                            scanJob?.cancel()
                            stopBleScan()
                            selectedDevice = device
                            testResults.clear()
                            testMessage = "Enter a count, then run the test."
                        }
                    )
                } else {
                    ConnectionTestScreen(
                        device = selected,
                        attemptCount = attemptCount,
                        onAttemptCountChange = { attemptCount = it.filter(Char::isDigit).take(2) },
                        isRunning = isRunning,
                        message = testMessage,
                        results = testResults,
                        hasActiveConnection = activeConnection != null,
                        onRunClick = ::checkAndRunConnections,
                        onDisconnectClick = ::disconnectActiveConnection,
                        onBackClick = { selectedDevice = null }
                    )
                }
            }
        }
    }

    private fun checkAndStartScan() {
        val environment = bleClient.environment.checkScan()
        when {
            !environment.bleSupported -> scanMessage = "BLE is not supported."
            !environment.bluetoothEnabled -> scanMessage = "Please enable Bluetooth."
            environment.missingPermissions.isNotEmpty() -> {
                scanPermissionLauncher.launch(environment.missingPermissions.toTypedArray())
            }
            else -> startBleScan()
        }
    }

    private fun startBleScan() {
        scanJob?.cancel()
        scannedDevices.clear()
        isScanning = true
        scanMessage = "Scanning for BLE devices..."
        scanJob = lifecycleScope.launch {
            bleClient.scan(BleScanConfig(allowDuplicates = false))
                .catch { error ->
                    scanMessage = (error as? BleException)?.error?.message
                        ?: error.message ?: "BLE scan failed."
                    Log.e(TAG, "Scan error", error)
                }
                .onCompletion {
                    isScanning = false
                    if (scanMessage == "Scanning for BLE devices...") {
                        scanMessage = "${scannedDevices.size} devices found."
                    }
                }
                .collect { result ->
                    if (scannedDevices.none { it.device.address == result.device.address }) {
                        scannedDevices.add(result)
                    }
                }
        }
    }

    private fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        lifecycleScope.launch { bleClient.stopScan() }
        isScanning = false
    }

    private fun checkAndRunConnections() {
        if (isRunning) return
        val count = attemptCount.toIntOrNull()
        if (count == null || count !in 1..20) {
            testMessage = "Enter a number from 1 to 20."
            return
        }
        val environment = bleClient.environment.checkConnect()
        when {
            !environment.bleSupported -> testMessage = "BLE is not supported."
            !environment.bluetoothEnabled -> testMessage = "Please enable Bluetooth."
            environment.missingPermissions.isNotEmpty() -> {
                pendingAttemptCount = count
                connectPermissionLauncher.launch(environment.missingPermissions.toTypedArray())
            }
            else -> startConnectionTest(count)
        }
    }

    private fun startConnectionTest(count: Int) {
        val device = selectedDevice ?: return
        if (isRunning) return
        testResults.clear()
        testMessage = "Starting $count simultaneous connection requests..."
        isRunning = true
        connectJob = lifecycleScope.launch {
            try {
                val jobs = (1..count).map { attempt ->
                    launch {
                        try {
                            val connection = bleClient.connect(device)
                            // Keep the first successful session alive to test AlreadyConnected.
                            // Extra successes are unexpected and must be closed.
                            if (activeConnection == null) {
                                activeConnection = connection
                                testResults.add("Attempt $attempt: CONNECTED")
                            } else {
                                testResults.add("Attempt $attempt: DUPLICATE CONNECTION SUCCEEDED")
                                connection.disconnect()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: BleException) {
                            val label = when (error.error) {
                                is BleError.AlreadyConnecting -> "ALREADY CONNECTING"
                                is BleError.AlreadyConnected -> "ALREADY CONNECTED"
                                else -> "FAILED: ${error.error.message}"
                            }
                            testResults.add("Attempt $attempt: $label")
                        } catch (error: Exception) {
                            testResults.add("Attempt $attempt: ERROR: ${error.message}")
                            Log.e(TAG, "Connection attempt $attempt failed", error)
                        }
                    }
                }
                jobs.joinAll()
                testMessage = "Finished $count requests. Review results below."
            } finally {
                isRunning = false
            }
        }
    }

    private fun disconnectActiveConnection() {
        val connection = activeConnection ?: return
        lifecycleScope.launch {
            try {
                connection.disconnect()
                testMessage = "Disconnect requested. Wait for GATT cleanup before retesting."
                // ConnectionState is updated asynchronously by the BLE library.
                connection.state.first { state ->
                    state is com.rama.blecore.connection.BleConnectionState.Disconnected ||
                            state is com.rama.blecore.connection.BleConnectionState.Failed
                }
                if (activeConnection === connection) activeConnection = null
                testMessage = "Connection terminated. You can run another test."
            } catch (error: Exception) {
                testMessage = "Disconnect error: ${error.message}"
            }
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        connectJob?.cancel()
        activeConnection?.let { connection ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { connection.disconnect() }
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RamaBleSample"
    }
}

@Composable
private fun ScannerScreen(
    devices: List<BleScanResult>,
    isScanning: Boolean,
    message: String,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit,
    onDeviceClick: (BleDevice) -> Unit
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Rama BLE Manager", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScanClick, enabled = !isScanning) {
                    Text(if (isScanning) "Scanning..." else "Scan BLE Devices")
                }
                if (isScanning) {
                    Button(onClick = onStopClick) { Text("Stop") }
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message)
            Spacer(Modifier.height(16.dp))
            Text("Devices (${devices.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.device.address }) { result ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onDeviceClick(result.device) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(result.device.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium)
                            Text("Address: ${result.device.address}")
                            Text("RSSI: ${result.rssi} dBm")
                            Text("Tap to test connections")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTestScreen(
    device: BleDevice,
    attemptCount: String,
    onAttemptCountChange: (String) -> Unit,
    isRunning: Boolean,
    message: String,
    results: List<String>,
    hasActiveConnection: Boolean,
    onRunClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(onClick = onBackClick, enabled = !isRunning && !hasActiveConnection) { Text("Back to devices") }
            Spacer(Modifier.height(12.dp))
            Text("Connection Test", style = MaterialTheme.typography.headlineMedium)
            Text(device.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium)
            Text(device.address)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = attemptCount,
                onValueChange = onAttemptCountChange,
                label = { Text("Number of connection requests (1–20)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRunClick, enabled = !isRunning) {
                    Text(if (isRunning) "Running..." else "Run Connect Test")
                }
                if (hasActiveConnection) {
                    Button(onClick = onDisconnectClick, enabled = !isRunning) {
                        Text("Disconnect")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(message)
            Spacer(Modifier.height(12.dp))
            Text("Results", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result -> Text(result) }
            }
        }
    }
}

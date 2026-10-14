package com.rama.ramablemanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rama.blecore.client.BleClient
import com.rama.blecore.client.BleClientConfig
import com.rama.blecore.client.BleClientFactory
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionPolicy
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanResult
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private var selectedPolicy by mutableStateOf(BleConnectionPolicy.SINGLE_DEVICE)
    private var currentScreen by mutableStateOf(SamplePage.HOME)
    private var selectedTestDevice by mutableStateOf<BleDevice?>(null)
    private var bleClient: BleClient? = null
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scannedDevices = mutableStateListOf<BleScanResult>()
    private val connections = mutableStateMapOf<String, BleConnection>()
    private val connectionStates = mutableStateMapOf<String, String>()
    private val busyAddresses = mutableStateListOf<String>()
    private val stateJobs = mutableMapOf<String, Job>()

    private var isScanning by mutableStateOf(false)
    private var isRunning by mutableStateOf(false)
    private var isDisconnecting by mutableStateOf(false)
    private var scanMessage by mutableStateOf("Press Scan to discover BLE devices")
    private var connectionMessage by mutableStateOf("Select a device to connect")
    private var attemptCount by mutableStateOf("3")
    private val scanTerminal = TerminalLog()
    private val connectionTerminal = TerminalLog()
    private val results = mutableStateListOf<String>()
    private var scanJob: Job? = null
    private var testJob: Job? = null
    private var pendingAction: (() -> Unit)? = null

    private fun key(address: String) = address.uppercase(Locale.ROOT)

    private fun client(): BleClient = bleClient ?: BleClientFactory.create(
        context = applicationContext,
        config = BleClientConfig(
            connectionPolicy = selectedPolicy,
            timeoutConfig = BleTimeoutConfig(scanTimeout = 2.seconds)
        )
    ).also { bleClient = it }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted.values.all { it }) action?.invoke()
        else {
            connectionMessage = "Bluetooth permission denied"
            connectionTerminal.add(connectionMessage, TerminalLevel.ERROR)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                when (currentScreen) {
                    SamplePage.HOME -> HomePage(
                        policy = selectedPolicy,
                        policyEnabled = bleClient == null && !isRunning && !isScanning && !isDisconnecting,
                        onPolicyChange = { policy ->
                            if (bleClient == null && !isRunning && !isScanning && !isDisconnecting) {
                                selectedPolicy = policy
                                connectionTerminal.add("Policy changed to $policy")
                            }
                        },
                        activeCount = connections.size,
                        onScan = { currentScreen = SamplePage.SCAN },
                        onConnections = { currentScreen = SamplePage.CONNECTIONS },
                        onLogs = { currentScreen = SamplePage.LOGS }
                    )
                    SamplePage.SCAN -> ScanPage(
                        devices = scannedDevices,
                        isScanning = isScanning,
                        message = scanMessage,
                        terminal = scanTerminal,
                        onScan = ::checkAndScan,
                        onStop = ::stopScan,
                        onBack = { currentScreen = SamplePage.HOME },
                        onSelect = { device ->
                            selectedTestDevice = device
                            currentScreen = SamplePage.DEVICE
                        }
                    )
                    SamplePage.DEVICE -> {
                        val device = selectedTestDevice
                        if (device != null) DevicePage(
                            device = device,
                            policy = selectedPolicy,
                            isRunning = isRunning,
                            isDisconnecting = isDisconnecting,
                            isScanning = isScanning,
                            isBusy = key(device.address) in busyAddresses,
                            isConnected = connections.containsKey(key(device.address)),
                            state = connectionStates[key(device.address)] ?: "Not connected",
                            message = connectionMessage,
                            attemptCount = attemptCount,
                            onAttemptCountChange = { attemptCount = it.filter(Char::isDigit).take(2) },
                            results = results,
                            terminal = connectionTerminal,
                            onConnect = { checkAndConnect(device) },
                            onDisconnect = { disconnectDevice(key(device.address)) },
                            onTest = { checkAndTest(device) },
                            onBack = { currentScreen = SamplePage.SCAN },
                            onConnections = { currentScreen = SamplePage.CONNECTIONS }
                        )
                    }
                    SamplePage.CONNECTIONS -> ConnectionsPage(
                        policy = selectedPolicy,
                        connections = connections,
                        states = connectionStates,
                        isRunning = isRunning,
                        isDisconnecting = isDisconnecting,
                        onDisconnect = ::disconnectDevice,
                        onDisconnectAll = ::disconnectAll,
                        onBack = { currentScreen = SamplePage.HOME },
                        onScan = { currentScreen = SamplePage.SCAN }
                    )
                    SamplePage.LOGS -> LogsPage(
                        scanTerminal = scanTerminal,
                        connectionTerminal = connectionTerminal,
                        onBack = { currentScreen = SamplePage.HOME }
                    )
                }
            }
        }
    }

    private fun checkPermissions(forScan: Boolean, action: () -> Unit) {
        val environment = if (forScan) client().environment.checkScan()
        else client().environment.checkConnect()
        when {
            !environment.bleSupported -> connectionMessage = "BLE not supported"
            !environment.bluetoothEnabled -> connectionMessage = "Enable Bluetooth first"
            environment.missingPermissions.isNotEmpty() -> {
                pendingAction = action
                permissionLauncher.launch(environment.missingPermissions.toTypedArray())
            }
            else -> action()
        }
    }

    private fun checkAndScan() = checkPermissions(forScan = true, action = ::startScan)

    private fun startScan() {
        if (isScanning || isRunning || isDisconnecting) return
        scannedDevices.clear()
        scanTerminal.clear()
        scanMessage = "Scanning..."
        isScanning = true
        scanJob = lifecycleScope.launch {
            client().scan(BleScanConfig(allowDuplicates = false))
                .catch { e ->
                    scanMessage = e.message ?: "Scan failed"
                    scanTerminal.add(scanMessage, TerminalLevel.ERROR)
                    Log.e(TAG, "Scan failed", e)
                }
                .onCompletion {
                    isScanning = false
                    if (scanMessage == "Scanning...") {
                        scanMessage = "Found ${scannedDevices.size} devices"
                    }
                    scanTerminal.add("Scan finished: ${scannedDevices.size} devices")
                }
                .collect { result ->
                    if (scannedDevices.none { key(it.device.address) == key(result.device.address) }) {
                        scannedDevices.add(result)
                        scanTerminal.add("Found ${result.device.name ?: "Unknown"} (${result.device.address})")
                    }
                }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        lifecycleScope.launch { client().stopScan() }
    }

    private fun checkAndConnect(device: BleDevice) =
        checkPermissions(forScan = false) { connectDevice(device) }

    private fun connectDevice(device: BleDevice) {
        if (isRunning || isDisconnecting) return
        val address = key(device.address)
        if (address in busyAddresses) {
            connectionTerminal.add("Already connecting $address", TerminalLevel.WARNING)
            return
        }
        busyAddresses.add(address)
        lifecycleScope.launch {
            try {
                connectOnce(device)
            } finally {
                busyAddresses.remove(address)
            }
        }
    }

    private suspend fun connectOnce(device: BleDevice): Boolean {
        val address = key(device.address)
        connectionTerminal.add("CONNECT requested: $address")
        return try {
            val connection = client().connect(device)
            // SINGLE_DEVICE replacement can terminate a previously tracked connection.
            // The state observer below removes it when it reaches a terminal state.
            if (connections[address] != null) {
                connectionTerminal.add("Unexpected duplicate success: $address", TerminalLevel.ERROR)
                connection.disconnect()
                false
            } else {
                connections[address] = connection
                connectionStates[address] = connection.state.value.toString()
                observeConnection(address, connection)
                connectionMessage = "Connected: $address"
                connectionTerminal.add("CONNECTED $address", TerminalLevel.SUCCESS)
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: BleException) {
            val label = when (e.error) {
                is BleError.AlreadyConnecting -> "ALREADY CONNECTING"
                is BleError.AlreadyConnected -> "ALREADY CONNECTED"
                else -> "FAILED: ${e.error.message}"
            }
            connectionTerminal.add("$address: $label", TerminalLevel.WARNING)
            connectionMessage = "$address: $label"
            false
        } catch (e: Exception) {
            connectionTerminal.add("$address: ${e.message}", TerminalLevel.ERROR)
            Log.e(TAG, "Connect failed", e)
            false
        }
    }

    private fun observeConnection(address: String, connection: BleConnection) {
        stateJobs.remove(address)?.cancel()
        stateJobs[address] = lifecycleScope.launch {
            connection.state.collect { state ->
                connectionStates[address] = state.toString()
                connectionTerminal.add("STATE $address: $state")
                if (state is BleConnectionState.Disconnected || state is BleConnectionState.Failed) {
                    if (connections[address] === connection) {
                        connections.remove(address)
                        connectionStates.remove(address)
                    }
                    connectionTerminal.add("TERMINAL $address", TerminalLevel.WARNING)
                    // Terminal state is not necessarily proof that GATT cleanup has completed.
                    stateJobs.remove(address)
                    cancel()
                }
            }
        }
    }

    private fun disconnectDevice(address: String) {
        val connection = connections[address] ?: return
        if (isRunning || isDisconnecting) return
        lifecycleScope.launch {
            isDisconnecting = true
            try {
                connectionTerminal.add("DISCONNECT requested: $address")
                connection.disconnect()
                // Registry cleanup may still be running after this returns.
                connectionMessage = "Disconnect requested for $address"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectionTerminal.add("Disconnect failed: ${e.message}", TerminalLevel.ERROR)
            } finally {
                isDisconnecting = false
            }
        }
    }

    private fun disconnectAll() {
        if (isRunning || isDisconnecting) return
        lifecycleScope.launch {
            isDisconnecting = true
            try {
                connections.values.toList().forEach { connection ->
                    try {
                        connectionTerminal.add("DISCONNECT ${connection.device.address}")
                        connection.disconnect()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        connectionTerminal.add("Disconnect error: ${e.message}", TerminalLevel.ERROR)
                    }
                }
            } finally {
                isDisconnecting = false
            }
        }
    }

    private fun checkAndTest(device: BleDevice) =
        checkPermissions(forScan = false) { startConcurrentTest(device) }

    private fun startConcurrentTest(device: BleDevice) {
        val count = attemptCount.toIntOrNull()
        if (count == null || count !in 1..20) {
            connectionMessage = "Enter an attempt count from 1 to 20"
            return
        }
        if (isRunning || isDisconnecting || isScanning) return
        results.clear()
        isRunning = true
        testJob = lifecycleScope.launch {
            try {
                val outcomes = (1..count).map { index ->
                    async {
                        val ok = connectOnce(device)
                        "Attempt $index: ${if (ok) "CONNECTED" else "REJECTED / FAILED"}"
                    }
                }.awaitAll()
                results.addAll(outcomes)
                connectionMessage = "Completed $count connection requests"
            } finally {
                isRunning = false
            }
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        testJob?.cancel()
        stateJobs.values.forEach { it.cancel() }
        val toDisconnect = connections.values.toList()
        shutdownScope.launch {
            toDisconnect.forEach { connection ->
                try { connection.disconnect() } catch (e: Exception) {
                    Log.e(TAG, "Shutdown disconnect failed", e)
                }
            }
        }
        super.onDestroy()
    }

    companion object { private const val TAG = "RamaBleSample" }
}


private enum class SamplePage { HOME, SCAN, DEVICE, CONNECTIONS, LOGS }

@Composable
private fun AppPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("‹ Back") }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    action()
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun HomePage(
    policy: BleConnectionPolicy,
    policyEnabled: Boolean,
    onPolicyChange: (BleConnectionPolicy) -> Unit,
    activeCount: Int,
    onScan: () -> Unit,
    onConnections: () -> Unit,
    onLogs: () -> Unit
) {
    AppPage("Rama BLE Manager", "BLE connection testing") {
        Text("CONNECTION MODE", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        BleConnectionPolicy.entries.forEach { option ->
            Card(
                onClick = { if (policyEnabled) onPolicyChange(option) },
                enabled = policyEnabled,
                colors = CardDefaults.cardColors(
                    containerColor = if (policy == option) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = policy == option,
                        onClick = { onPolicyChange(option) }, enabled = policyEnabled)
                    Column(Modifier.weight(1f)) {
                        Text(option.name.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (option == BleConnectionPolicy.SINGLE_DEVICE)
                                "Connecting a new device replaces the previous connection."
                            else "Connect multiple different devices simultaneously.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (!policyEnabled) Text(
            "Policy locked for this client. Restart the app to change it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Scan BLE devices")
        }
        OutlinedButton(onClick = onConnections, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Active connections ($activeCount)")
        }
        OutlinedButton(onClick = onLogs, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("View terminal logs")
        }
        Spacer(Modifier.weight(1f))
        Text("Test duplicate requests, switching, and independent connections.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScanPage(
    devices: List<BleScanResult>,
    isScanning: Boolean,
    message: String,
    terminal: TerminalLog,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onSelect: (BleDevice) -> Unit
) {
    AppPage(
        title = "Discover devices",
        subtitle = "${devices.size} device(s) found",
        onBack = onBack,
        action = {
            if (isScanning) TextButton(onClick = onStop) { Text("Stop") }
            else TextButton(onClick = onScan) { Text("Scan") }
        }
    ) {
        if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (devices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No devices listed yet", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onScan, enabled = !isScanning) { Text("Start scan") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.device.address }) { result ->
                    Card(onClick = { onSelect(result.device) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(result.device.name ?: "Unknown device",
                                    style = MaterialTheme.typography.titleMedium)
                                Text(result.device.address,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("RSSI ${result.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
        // Logs live on their own page; keep scanning focused on device discovery.
    }
}

@Composable
private fun DevicePage(
    device: BleDevice,
    policy: BleConnectionPolicy,
    isRunning: Boolean,
    isDisconnecting: Boolean,
    isScanning: Boolean,
    isBusy: Boolean,
    isConnected: Boolean,
    state: String,
    message: String,
    attemptCount: String,
    onAttemptCountChange: (String) -> Unit,
    results: List<String>,
    terminal: TerminalLog,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    onConnections: () -> Unit
) {
    AppPage(title = "Device test", subtitle = device.name ?: device.address, onBack = onBack) {
        InfoCard(device.name ?: "Unknown device", device.address)
        Text("Mode: ${policy.name.replace('_', ' ')}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Text("Connection state", style = MaterialTheme.typography.labelMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)) {
            Text(state, Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConnect,
                enabled = !isRunning && !isDisconnecting && !isScanning && !isBusy,
                modifier = Modifier.weight(1f)
            ) { Text(if (isBusy) "Connecting..." else "Connect") }
            OutlinedButton(
                onClick = onDisconnect,
                enabled = isConnected && !isRunning && !isDisconnecting,
                modifier = Modifier.weight(1f)
            ) { Text("Disconnect") }
        }
        HorizontalDivider()
        Text("Concurrent connection test", style = MaterialTheme.typography.titleMedium)
        Text("Send multiple requests to this same device to test duplicate protection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = attemptCount,
            onValueChange = onAttemptCountChange,
            label = { Text("Requests (1–20)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onTest,
            enabled = !isRunning && !isDisconnecting && !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isRunning) "Running test..." else "Run test") }
        if (isRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (results.isNotEmpty()) {
            Text("Results (${results.size})", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results) { result ->
                    Text(result, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        OutlinedButton(onClick = onConnections, modifier = Modifier.fillMaxWidth()) {
            Text("View active connections")
        }
    }
}

@Composable
private fun ConnectionsPage(
    policy: BleConnectionPolicy,
    connections: Map<String, BleConnection>,
    states: Map<String, String>,
    isRunning: Boolean,
    isDisconnecting: Boolean,
    onDisconnect: (String) -> Unit,
    onDisconnectAll: () -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit
) {
    AppPage(title = "Active connections", subtitle = "${connections.size} tracked", onBack = onBack) {
        Text("Policy: ${policy.name.replace('_', ' ')}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        if (connections.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No active connections", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(connections.entries.toList(), key = { it.key }) { (address, connection) ->
                    InfoCard(
                        title = connection.device.name ?: "Unknown device",
                        detail = "$address\n${states[address] ?: "Unknown state"}",
                        trailing = {
                            TextButton(
                                onClick = { onDisconnect(address) },
                                enabled = !isRunning && !isDisconnecting
                            ) { Text("Disconnect") }
                        }
                    )
                }
            }
            OutlinedButton(
                onClick = onDisconnectAll,
                enabled = !isRunning && !isDisconnecting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Disconnect all") }
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan / connect another device")
        }
    }
}

@Composable
private fun LogsPage(
    scanTerminal: TerminalLog,
    connectionTerminal: TerminalLog,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    AppPage(title = "Terminal logs", subtitle = "Live BLE events", onBack = onBack) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("Connections") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("Scanner") })
        }
        val log = if (selectedTab == 0) connectionTerminal else scanTerminal
        TerminalConsole(
            title = if (selectedTab == 0) "CONNECTION TERMINAL" else "SCAN TERMINAL",
            log = log,
            modifier = Modifier.weight(1f)
        )
    }
}

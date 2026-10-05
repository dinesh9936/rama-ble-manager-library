package com.rama.ramablemanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.scan.BleScanResult

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                BleSampleScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun BleSampleScreen(
    viewModel: MainViewModel
) {

    val environment by viewModel.environment.collectAsState()

    val devices by viewModel.devices.collectAsState()

    val connectionState by viewModel.connectionState.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) { result ->

            Log.d(
                TAG,
                "permission result: $result"
            )

            viewModel.checkEnvironment()
        }

    LaunchedEffect(Unit) {
        viewModel.checkEnvironment()
    }

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
                    MaterialTheme.typography.headlineSmall
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text =
                    "Environment ready: ${
                        environment?.isReady == true
                    }"
            )

            environment?.let { state ->

                if (state.missingPermissions.isNotEmpty()) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Missing permissions: ${
                                state.missingPermissions.joinToString()
                            }"
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {

                            Log.d(
                                TAG,
                                "requesting permissions: ${state.missingPermissions}"
                            )

                            permissionLauncher.launch(
                                state.missingPermissions
                                    .toTypedArray()
                            )
                        }
                    ) {
                        Text("Grant BLE Permissions")
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {
                        viewModel.startScan()
                    },
                    enabled =
                        environment?.isReady == true
                ) {
                    Text("Start Scan")
                }

                Button(
                    onClick = {
                        viewModel.stopScan()
                    }
                ) {
                    Text("Stop")
                }

                Button(
                    onClick = {
                        viewModel.disconnect()
                    }
                ) {
                    Text("Disconnect")
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text =
                    "Connection: ${
                        connectionState.toDisplayText()
                    }"
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Discovered devices",
                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            LazyColumn(
                modifier =
                    Modifier.fillMaxSize(),
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
                        result = result,
                        onClick = {
                            viewModel.connect(
                                result.device
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BleDeviceItem(
    result: BleScanResult,
    onClick: () -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick
                )
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
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = result.device.address
            )

            Text(
                text =
                    "RSSI: ${result.rssi} dBm"
            )

            if (result.serviceUuids.isNotEmpty()) {

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                result.serviceUuids.forEach {
                    Text(
                        text = "Service: $it"
                    )
                }
            }
        }
    }
}

private fun BleConnectionState
        .toDisplayText(): String {

    return when (this) {
        BleConnectionState.Idle -> "Idle"
        is BleConnectionState.Connecting -> "Connecting to ${device.name ?: device.address}"
        is BleConnectionState.Connected -> "Connected to ${device.name ?: device.address}"
        is BleConnectionState.Disconnecting -> "Disconnecting"
        is BleConnectionState.Disconnected -> "Disconnected"
        is BleConnectionState.Failed -> "Failed: ${error.message}"
    }
}

private const val TAG =
    "RamaBleSample"
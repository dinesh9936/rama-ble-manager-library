package com.rama.ramablemanager.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rama.blecore.api.BleClientFactory
import com.rama.blecore.api.BleConfig
import com.rama.blecore.exceptions.BleScanError
import com.rama.blecore.model.BleConnectionState
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun BleTestScreen() {

    val TAG = "BleTestScreen"

    val context = LocalContext.current

    val bleClient = remember {
        BleClientFactory.create(
            context = context,
            config = BleConfig(
                enableLogging = true,
                autoReconnect = true,
                reconnectAttempts = 3,
                reconnectDelayMillis = 1000
            )
        )
    }

    val devices = remember { mutableStateListOf<BleDevice>() }
    val connectionState = remember { mutableStateOf<BleConnectionState?>(null) }

    Column(Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Button(onClick = {
            // Start scanning
            CoroutineScope(Dispatchers.Main).launch {
                bleClient.scanDevices(
                    scanTimeout = 2000,
                    deviceName = "MIJ00100052",
                    isDeviceNamePrefix = false
                ).catch { e ->
                    if (e is BleScanError) {
                        val msg = e.errorMessage
                        val code = e.errorCode
                        Log.e(TAG, "BleTestScreen: $msg")
                        Log.e(TAG, "BleTestScreen: $code")
                    }else{
                        Log.e(TAG, "BleTestScreen: ${e.message}")
                    }
                }

                    .collect { device ->

                    if (devices.none { it.address == device.address }) {
                        devices.add(device)
                    }
                }
            }
        }) {
            Text("Start Scan")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(devices) { device ->
                Text(
                    text = "${device.name} - ${device.address}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            CoroutineScope(Dispatchers.Main).launch {
                                bleClient.connect(device.address).collect { state ->
                                    connectionState.value = state
                                }
                            }
                        }
                        .padding(8.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        connectionState.value?.let { state ->
            Text("STATE: $state")
        }
    }
}

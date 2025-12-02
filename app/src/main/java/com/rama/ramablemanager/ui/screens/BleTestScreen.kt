package com.rama.ramablemanager.ui.screens

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rama.blecore.api.BleClientFactory
import com.rama.blecore.api.BleConfig
import com.rama.blecore.exceptions.BleScanError
import com.rama.blecore.model.BleConnectionState
import com.rama.blecore.model.BleDevice
import com.rama.blecore.model.BleScanState
import com.rama.ramablemanager.ui.components.BleDeviceCard
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
            config = BleConfig()
        )
    }

    val devices = remember { mutableStateListOf<BleDevice>() }
    val connectionState = remember { mutableStateOf<BleConnectionState?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(devices) { device ->

                if (device.name != "Unknown"){
                    BleDeviceCard(
                        bleDevice = device,
                        onClick = {}
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        connectionState.value?.let { state ->
            Text("STATE: $state")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                CoroutineScope(Dispatchers.Main).launch {
                    bleClient.scanDevices()
                        .catch { e ->
                            if (e is BleScanError) {
                                Log.e(TAG, "BleTestScreen: ${e.message} and ${e.errorCode}")
                            } else {
                                Log.e(TAG, "BleTestScreen: ${e.message}")
                            }
                        }
                        .collect { device ->
                            when(device){
                                is BleScanState.Started->{
                                    devices.clear()
                                    Log.d(TAG, "BleTestScreen: started")
                                }
                                is BleScanState.Scanning->{
                                    Log.d(TAG, "BleTestScreen: scanning")
                                }
                                is BleScanState.Stopped->{
                                    Log.d(TAG, "BleTestScreen: stopped")
                                }
                                is BleScanState.Error->{
                                    Log.d(TAG, "BleTestScreen: ${device.error.message}")
                                }
                                is BleScanState.DeviceFound->{
                                    if (devices.none { it.address == device.device.name }) {
                                        devices.add(device.device)
                                    }
                                }
                                is BleScanState.Timeout->{
                                    Log.d(TAG, "BleTestScreen: timeout")
                                }
                            }
                            
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("START SCAN")
        }
    }
}


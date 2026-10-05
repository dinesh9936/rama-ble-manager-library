package com.rama.ramablemanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rama.blecore.client.BleClient
import com.rama.blecore.client.BleClientFactory
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.environment.BleEnvironmentState
import com.rama.blecore.model.BleDevice
import com.rama.blecore.scan.BleScanConfig
import com.rama.blecore.scan.BleScanMode
import com.rama.blecore.scan.BleScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val bleClient: BleClient = BleClientFactory.create(context = application)

    private val _environment = MutableStateFlow<BleEnvironmentState?>(null)

    val environment: StateFlow<BleEnvironmentState?> = _environment.asStateFlow()

    private val _devices = MutableStateFlow<List<BleScanResult>>(emptyList())

    val devices: StateFlow<List<BleScanResult>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)

    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private var scanJob: Job? = null

    private var connection: BleConnection? = null

    fun checkEnvironment() {

        Log.d(
            TAG,
            "checkEnvironment: checking BLE environment"
        )

        val state =
            bleClient.environment.check()

        _environment.value =
            state

        Log.d(
            TAG,
            """
            checkEnvironment:
            bleSupported=${state.bleSupported}
            bluetoothEnabled=${state.bluetoothEnabled}
            locationRequired=${state.locationServiceRequired}
            locationEnabled=${state.locationServiceEnabled}
            missingPermissions=${state.missingPermissions}
            isReady=${state.isReady}
            """.trimIndent()
        )
    }

    fun startScan() {

        Log.d(
            TAG,
            "startScan: requested"
        )

        stopScan()

        _devices.value = emptyList()

        scanJob = viewModelScope.launch {
            runCatching {
                bleClient.scan(BleScanConfig(scanMode = BleScanMode.LOW_LATENCY, allowDuplicates = false)
                    ).collect { result ->

                        Log.d(
                            TAG,
                            """
                            scanResult:
                            name=${result.device.name}
                            address=${result.device.address}
                            rssi=${result.rssi}
                            services=${result.serviceUuids}
                            """.trimIndent()
                        )

                        val current =
                            _devices.value

                        val updated =
                            current
                                .filterNot {
                                    it.device.address ==
                                            result.device.address
                                } +
                                    result

                        _devices.value =
                            updated
                                .sortedByDescending {
                                    it.rssi
                                }
                    }

                }.onFailure { throwable ->

                    Log.e(
                        TAG,
                        "startScan: scan failed",
                        throwable
                    )
                }
            }
    }

    fun stopScan() {

        Log.d(
            TAG,
            "stopScan: requested"
        )

        scanJob?.cancel()
        scanJob = null

        viewModelScope.launch {

            runCatching {
                bleClient.stopScan()
            }.onSuccess {

                Log.d(
                    TAG,
                    "stopScan: scan stopped"
                )

            }.onFailure { throwable ->

                Log.e(
                    TAG,
                    "stopScan: failed",
                    throwable
                )
            }
        }
    }

    fun connect(
        device: BleDevice
    ) {

        Log.d(
            TAG,
            """
            connect:
            name=${device.name}
            address=${device.address}
            """.trimIndent()
        )

        viewModelScope.launch {

            stopScan()

            runCatching {

                bleClient.connect(
                    device = device
                )

            }.onSuccess { connected ->

                Log.d(
                    TAG,
                    "connect: connection object created"
                )

                connection =
                    connected

                connected.state.collect { state ->

                    Log.d(
                        TAG,
                        "connectionState: $state"
                    )

                    _connectionState.value =
                        state
                }

            }.onFailure { throwable ->

                connection =
                    null

                Log.e(
                    TAG,
                    "connect: failed for ${device.address}",
                    throwable
                )
            }
        }
    }

    fun disconnect() {

        Log.d(
            TAG,
            "disconnect: requested"
        )

        viewModelScope.launch {

            val activeConnection =
                connection

            if (activeConnection == null) {

                Log.d(
                    TAG,
                    "disconnect: no active connection"
                )

                return@launch
            }

            runCatching {

                activeConnection.disconnect()

            }.onSuccess {

                Log.d(
                    TAG,
                    "disconnect: request sent"
                )

                connection = null

            }.onFailure { throwable ->

                Log.e(
                    TAG,
                    "disconnect: failed",
                    throwable
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        Log.d(
            TAG,
            "onCleared"
        )

        scanJob?.cancel()
    }

    private companion object {
        const val TAG =
            "RamaBleSample"
    }
}
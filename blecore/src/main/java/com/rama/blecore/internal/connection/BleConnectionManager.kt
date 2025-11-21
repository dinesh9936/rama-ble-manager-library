package com.rama.blecore.internal.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import androidx.annotation.RequiresPermission
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.model.BleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

class BleConnectionManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var bluetoothGatt: BluetoothGatt? = null

    private var autoReconnectEnabled = false
    private var reconnectAttempts = 0
    private var reconnectDelay = 0L
    private var lastDeviceAddress: String? = null

    private val connectionStateChannel =
        Channel<BleConnectionState>(Channel.BUFFERED)

    private val gattCallback = BleGattCallback(connectionStateChannel)

    private val commandQueue = BleCommandQueue()


    // ----------------------------------------------------------
    // PUBLIC API
    // ----------------------------------------------------------

    fun configureAutoReconnect(
        enabled: Boolean,
        attempts: Int,
        delayMillis: Long
    ) {
        autoReconnectEnabled = enabled
        reconnectAttempts = attempts
        reconnectDelay = delayMillis

        BleLogger.d(
            "AutoReconnect configured → enabled=$enabled attempts=$attempts delay=${delayMillis}ms"
        )
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String): Flow<BleConnectionState> {
        BleLogger.d("Connecting to: $address")

        lastDeviceAddress = address

        val device: BluetoothDevice? = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            BleLogger.e("Invalid device address: $address")
            connectionStateChannel.trySend(BleConnectionState.Failed("Invalid MAC address"))
            return connectionStateChannel.receiveAsFlow()
        }

        bluetoothGatt = device?.connectGatt(
            context,
            false,
            gattCallback
        )

        return connectionStateChannel.receiveAsFlow()
    }


    fun disconnect() {
        BleLogger.d("Disconnect() called by client")

        autoReconnectEnabled = false
        lastDeviceAddress = null

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }


    fun getGatt(): BluetoothGatt? = bluetoothGatt

    fun getGattCallback(): BleGattCallback = gattCallback

    fun getCommandQueue(): BleCommandQueue = commandQueue


    // ----------------------------------------------------------
    // AUTO RECONNECT HANDLER
    // ----------------------------------------------------------

    fun handleAutoReconnectTrigger() {
        if (!autoReconnectEnabled || lastDeviceAddress == null) return

        BleLogger.w("AutoReconnect triggered for ${lastDeviceAddress}")

        scope.launch {
            repeat(reconnectAttempts) { attempt ->
                BleLogger.w("AutoReconnect: attempt ${attempt + 1}/$reconnectAttempts")

                delay(reconnectDelay)

                val addr = lastDeviceAddress ?: return@launch
                val flow = connect(addr)

                // optional: collect briefly to observe connection state
                scope.launch {
                    flow.collect { state ->
                        if (state is BleConnectionState.Connected) {
                            BleLogger.d("AutoReconnect SUCCESS")
                            return@collect
                        }
                    }
                }
            }

            BleLogger.e("AutoReconnect: exhausted all attempts.")
        }
    }


    // ----------------------------------------------------------
    // HOOK for DISCONNECT EVENT
    // ----------------------------------------------------------

    fun onDeviceDisconnected() {
        BleLogger.d("ConnectionManager detected DISCONNECTED event")

        if (autoReconnectEnabled && lastDeviceAddress != null) {
            handleAutoReconnectTrigger()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): Boolean {

        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUUID) ?: return false
        val characteristic = service.getCharacteristic(characteristicUUID) ?: return false

        BleLogger.d("Enabling notifications for $characteristicUUID")

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        ) ?: return false

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    fun observeCharacteristic(): Flow<ByteArray> {
        return gattCallback.notifyChannel.receiveAsFlow()
    }
}

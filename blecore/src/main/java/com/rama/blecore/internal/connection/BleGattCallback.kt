package com.rama.blecore.internal.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.model.BleConnectionState
import kotlinx.coroutines.channels.Channel

class BleGattCallback(
    private val connectionStateChannel: Channel<BleConnectionState>
) : BluetoothGattCallback() {

    val notifyChannel = Channel<ByteArray>(Channel.BUFFERED)
    val readChannel = Channel<ByteArray>(Channel.BUFFERED)
    val writeChannel = Channel<Boolean>(Channel.BUFFERED)

    private val serviceManager = BleServiceManager()

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?, status: Int, newState: Int
    ) {
        BleLogger.d("Connection state changed: newState=$newState status=$status")

        when (newState) {

            BluetoothProfile.STATE_CONNECTING -> {
                connectionStateChannel.trySend(BleConnectionState.Connecting)
            }

            BluetoothProfile.STATE_CONNECTED -> {
                connectionStateChannel.trySend(BleConnectionState.Connected)
                gatt?.let { serviceManager.updateGatt(it) }
                gatt?.discoverServices()
            }

            BluetoothProfile.STATE_DISCONNECTING -> {
                connectionStateChannel.trySend(BleConnectionState.Disconnecting)
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                connectionStateChannel.trySend(BleConnectionState.Disconnected)
                gatt?.close()

            }

            else -> {
                connectionStateChannel.trySend(
                    BleConnectionState.Failed("Unknown state: $newState")
                )
            }
        }

        // This check was wrong in your code!
        // newState != GATT_SUCCESS makes no sense.
        if (status != BluetoothGatt.GATT_SUCCESS) {
            connectionStateChannel.trySend(
                BleConnectionState.Failed("Error code: $status")
            )
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        BleLogger.d("Services discovered: status=$status")

        if (status == BluetoothGatt.GATT_SUCCESS) {
            connectionStateChannel.trySend(BleConnectionState.Connected)
        } else {
            connectionStateChannel.trySend(
                BleConnectionState.Failed("Service discovery failed: $status")
            )
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
            readChannel.trySend(characteristic.value)
        } else {
            readChannel.trySend(ByteArray(0))
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
        writeChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        BleLogger.d("Notification received: ${characteristic.uuid} → ${value.size} bytes")
        notifyChannel.trySend(value)
    }
}

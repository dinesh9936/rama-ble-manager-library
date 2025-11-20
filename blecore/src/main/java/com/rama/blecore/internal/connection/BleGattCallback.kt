package com.rama.blecore.internal.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.rama.blecore.model.BleConnectionState
import kotlinx.coroutines.channels.Channel

class BleGattCallback (
    private val connectionStateChannel: Channel<BleConnectionState>
): BluetoothGattCallback() {

    val notifyChannel = Channel<ByteArray>(Channel.BUFFERED)

    private val serviceManager = BleServiceManager()
    val readChannel = Channel<ByteArray>(Channel.BUFFERED)
    val writeChannel = Channel<Boolean>(Channel.BUFFERED)

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {

        when (newState) {
            BluetoothProfile.STATE_CONNECTING -> {
                connectionStateChannel.trySend(BleConnectionState.Connecting)
            }
            BluetoothProfile.STATE_CONNECTED -> {
                connectionStateChannel.trySend(BleConnectionState.Connected)
                gatt?.let { serviceManager.updateGatt(it) }
                gatt?.discoverServices()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                connectionStateChannel.trySend(BleConnectionState.Disconnected)
                gatt?.close()
            }
            BluetoothProfile.STATE_DISCONNECTING -> {
                connectionStateChannel.trySend(BleConnectionState.Disconnecting)
            }


            else -> {
                connectionStateChannel.trySend(BleConnectionState.Failed("Unknown state: $newState"))
            }
        }

        if (newState != BluetoothGatt.GATT_SUCCESS){
            connectionStateChannel.trySend(BleConnectionState.Failed("Error code: $status"))
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Services discovered successfully
            connectionStateChannel.trySend(BleConnectionState.Connected)
        } else {
            connectionStateChannel.trySend(BleConnectionState.Failed("Service discovery failed with status: $status"))
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
            characteristic.value?.let {
                readChannel.trySend(it)
            }
        } else {
            readChannel.trySend(ByteArray(0)) // Indicate failure with empty byte array
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        writeChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        characteristic.value?.let { data->
            notifyChannel.trySend(data)
         }
    }


}
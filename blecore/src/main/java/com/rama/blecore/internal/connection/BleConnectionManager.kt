package com.rama.blecore.internal.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import androidx.annotation.RequiresPermission
import com.rama.blecore.model.BleConnectionState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

class BleConnectionManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private var bluetoothGatt: BluetoothGatt? = null

    private val commandQueue = BleCommandQueue()

    fun getCommandQueue(): BleCommandQueue = commandQueue

    fun getGattCallback(): BleGattCallback = gattCallback


    fun getGatt(): BluetoothGatt? = bluetoothGatt

    private val connectionStateChannel = Channel<BleConnectionState>(
        capacity = Channel.BUFFERED
    )

    private val gattCallback = BleGattCallback(connectionStateChannel)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String): Flow<BleConnectionState>{
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(context, false, gattCallback)

        return connectionStateChannel.receiveAsFlow()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(){
        bluetoothGatt?.disconnect()
        bluetoothGatt = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): Boolean{
        val gatt = bluetoothGatt ?: return false

        val service = gatt.getService(serviceUUID) ?: return false
        val characteristic = service.getCharacteristic(characteristicUUID) ?: return false
        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            ?: return false
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    fun observeCharacteristic(): Flow<ByteArray>{
        return gattCallback.notifyChannel.receiveAsFlow()
    }
}
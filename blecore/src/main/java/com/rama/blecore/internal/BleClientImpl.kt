package com.rama.blecore.internal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.annotation.RequiresPermission
import com.rama.blecore.api.BleClient
import com.rama.blecore.api.BleConfig
import com.rama.blecore.internal.connection.BleConnectionManager
import com.rama.blecore.internal.connection.BleOperations
import com.rama.blecore.internal.scanner.BleScanner
import com.rama.blecore.model.BleConnectionState
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BleClientImpl(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    config: BleConfig,
): BleClient {


    private val scanner = BleScanner(bluetoothAdapter)
    private val connectionManager = BleConnectionManager(
        context,
        bluetoothAdapter
    )


    override fun scanDevices(): Flow<BleDevice> {
        return scanner.scanDevices()
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(address: String): Flow<BleConnectionState> {
        return connectionManager.connect(address)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect() {
        connectionManager.disconnect()
    }

    override suspend fun readCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): ByteArray? {
        val gatt = connectionManager.getGatt() ?: throw IllegalStateException("Not connected to a device")
        val operations = BleOperations(
            gatt = gatt,
            gattCallback = connectionManager.getGattCallback(),
            commandQueue = connectionManager.getCommandQueue()
        )
        val characteristic = gatt.getService(serviceUUID)?.getCharacteristic(characteristicUUID) ?: return null
        return operations.readCharacteristic(characteristic)
    }

    override suspend fun writeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray
    ): Boolean {
        val gatt = connectionManager.getGatt() ?: throw IllegalStateException("Not connected to a device")
        val operations = BleOperations(
            gatt = gatt,
            gattCallback = connectionManager.getGattCallback(),
            commandQueue = connectionManager.getCommandQueue())
        val characteristic = gatt.getService(serviceUUID)?.getCharacteristic(characteristicUUID) ?: return false
        return operations.writeCharacteristic(characteristic, data)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun observeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): Flow<ByteArray> {
        connectionManager.enableNotifications(serviceUUID,characteristicUUID)
        return connectionManager.observeCharacteristic()
    }
}
package com.rama.blecore.internal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.annotation.RequiresPermission
import com.rama.blecore.api.BleClient
import com.rama.blecore.api.BleConfig
import com.rama.blecore.exceptions.BleError
import com.rama.blecore.internal.connection.BleConnectionManager
import com.rama.blecore.internal.connection.BleOperations
import com.rama.blecore.internal.scanner.BleScanner
import com.rama.blecore.internal.utils.BleLogger
import com.rama.blecore.internal.utils.retryIO
import com.rama.blecore.model.BleConnectionState
import com.rama.blecore.model.BleDevice
import com.rama.blecore.model.BleScanState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BleClientImpl(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    config: BleConfig,
) : BleClient {

    init {
        BleLogger.enableLogging = config.enableLogging
        BleLogger.d("BleClient initialized with logging = ${config.enableLogging}")
    }

    private val retryCount = config.retryCount
    private val retryDelay = config.retryDelayMillis

    private val scanner = BleScanner(bluetoothAdapter, context)
    private val connectionManager = BleConnectionManager(
        context,
        bluetoothAdapter
    ).apply {
        configureAutoReconnect(
            enabled = config.autoReconnect,
            attempts = config.reconnectAttempts,
            delayMillis = config.reconnectDelayMillis
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun scanDevices(
        scanServiceUUID: String,
        scanTimeout: Long,
        deviceName: String,
        isDeviceNamePrefix: Boolean
    ): Flow<BleScanState> {
        return scanner.scanDevices(
            scanTimeout = scanTimeout,
            scanServiceUUID = scanServiceUUID,
            deviceName = deviceName,
            isDeviceNamePrefix = isDeviceNamePrefix
        )
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

        val gatt = connectionManager.getGatt()
            ?: throw BleError.DeviceDisconnected()

        val service = gatt.getService(serviceUUID)
            ?: throw BleError.ServiceNotFound()

        val characteristic = service.getCharacteristic(characteristicUUID)
            ?: throw BleError.CharacteristicNotFound()

        val operations = BleOperations(
            gatt = gatt,
            gattCallback = connectionManager.getGattCallback(),
            commandQueue = connectionManager.getCommandQueue()
        )

        return try {
            retryIO(times = retryCount, delayMillis = retryDelay) {
                operations.readCharacteristic(characteristic)
            }
        } catch (e: TimeoutCancellationException) {
            throw BleError.OperationTimeout()
        }
    }

    override suspend fun writeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray
    ): Boolean {

        val gatt = connectionManager.getGatt()
            ?: throw BleError.DeviceDisconnected()

        val service = gatt.getService(serviceUUID)
            ?: throw BleError.ServiceNotFound()

        val characteristic = service.getCharacteristic(characteristicUUID)
            ?: throw BleError.CharacteristicNotFound()

        val operations = BleOperations(
            gatt = gatt,
            gattCallback = connectionManager.getGattCallback(),
            commandQueue = connectionManager.getCommandQueue()
        )

        return try {
            retryIO(times = retryCount, delayMillis = retryDelay) {
                operations.writeCharacteristic(characteristic, data)
            }
        } catch (e: TimeoutCancellationException) {
            throw BleError.OperationTimeout()
        } == true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun observeCharacteristic(
        serviceUUID: UUID,
        characteristicUUID: UUID
    ): Flow<ByteArray> {

        connectionManager.enableNotifications(serviceUUID, characteristicUUID)

        return connectionManager.observeCharacteristic()
    }
}

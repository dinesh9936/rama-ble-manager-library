package com.rama.blecore.internal.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.connection.BleConnector
import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.internal.adapter.AndroidBluetoothAdapterProvider
import com.rama.blecore.internal.gatt.AndroidBleGattClient
import com.rama.blecore.internal.gatt.GattCallbackHandler
import com.rama.blecore.internal.gatt.GattMapper
import com.rama.blecore.internal.gatt.GattOperationCoordinator
import com.rama.blecore.internal.retry.DefaultBleRetryStrategy
import com.rama.blecore.model.BleDevice
import com.rama.blecore.timeout.BleTimeoutConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal class AndroidBleConnector(
    context: Context,
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val timeoutConfig: BleTimeoutConfig,
    private val retryStrategy: DefaultBleRetryStrategy =
        DefaultBleRetryStrategy()
) : BleConnector {

    private val appContext =
        context.applicationContext

    override suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection {

        var retryAttempt = 0

        while (true) {

            try {

                return connectOnce(
                    device = device,
                    config = config
                )

            } catch (cancellation: CancellationException) {

                throw cancellation

            } catch (throwable: Throwable) {

                val shouldRetry =
                    retryStrategy.shouldRetry(
                        attempt = retryAttempt,
                        policy = config.retryPolicy
                    )

                if (!shouldRetry) {
                    throw throwable
                }

                val retryDelay =
                    retryStrategy.nextDelay(
                        attempt = retryAttempt,
                        policy = config.retryPolicy
                    )

                retryAttempt++

                if (retryDelay.isPositive()) {
                    delay(retryDelay)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectOnce(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection {

        val adapter =
            adapterProvider.getAdapter()
                ?: throw BleException(
                    BleError.BluetoothUnavailable()
                )

        if (!adapter.isEnabled) {
            throw BleException(
                BleError.BluetoothDisabled()
            )
        }

        val bluetoothDevice =
            try {

                adapter.getRemoteDevice(
                    device.address
                )

            } catch (throwable: IllegalArgumentException) {

                throw BleException(
                    BleError.ConnectionFailed(
                        deviceAddress = device.address,
                        message =
                            "Invalid Bluetooth device address",
                        cause = throwable
                    )
                )
            }

        val stateBridge =
            ConnectionStateBridge()

        val operationCoordinator = GattOperationCoordinator()
        val gattMapper =
            GattMapper()

        val callbackHandler =
            GattCallbackHandler(
                operationCoordinator =
                    operationCoordinator,
                gattMapper =
                    gattMapper
            ) { gatt, status, newState ->

                handleConnectionStateChange(
                    gatt = gatt,
                    status = status,
                    newState = newState,
                    device = device,
                    stateBridge = stateBridge
                )
            }

        val bluetoothGatt =
            try {

                bluetoothDevice.connectGatt(
                    appContext,
                    config.autoConnect,
                    callbackHandler,
                    BluetoothDevice.TRANSPORT_LE
                )

            } catch (securityException: SecurityException) {

                throw BleException(
                    BleError.PermissionDenied(
                        permissions = emptyList(),
                        message =
                            "Bluetooth connect permission is missing"
                    )
                )

            } catch (throwable: Throwable) {

                throw BleException(
                    BleError.ConnectionFailed(
                        deviceAddress = device.address,
                        cause = throwable
                    )
                )
            }
                ?: throw BleException(
                    BleError.ConnectionFailed(
                        deviceAddress = device.address,
                        message =
                            "Android returned null BluetoothGatt"
                    )
                )

        val gattClient =
            AndroidBleGattClient(
                bluetoothGatt = bluetoothGatt,
                callbackHandler = callbackHandler,
                operationCoordinator = operationCoordinator,
                timeoutConfig = timeoutConfig
            )

        val connection =
            AndroidBleConnection(
                device = device,
                gatt = gattClient,
                disconnectAction = {

                    try {
                        bluetoothGatt.disconnect()
                    } catch (_: SecurityException) {
                        bluetoothGatt.close()
                    }
                }
            )

        stateBridge.attach(connection)

        return try {

            val connection =
                withTimeout(
                    config.connectionTimeout
                ) {
                    stateBridge.connectionDeferred.await()
                }

            config.preferredMtu?.let { mtu ->
                connection.gatt.requestMtu(mtu)
            }

            connection

        } catch (cancellation: CancellationException) {

            closeGatt(bluetoothGatt)
            throw cancellation

        } catch (throwable: Throwable) {

            closeGatt(bluetoothGatt)

            if (throwable is BleException) {
                throw throwable
            }

            throw BleException(
                BleError.Timeout(
                    operation =
                        "connect:${device.address}",
                    message =
                        "BLE connection timed out"
                )
            )
        }
    }

    private fun handleConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int,
        device: BleDevice,
        stateBridge: ConnectionStateBridge
    ) {

        if (status != BluetoothGatt.GATT_SUCCESS) {

            val error =
                BleError.ConnectionFailed(
                    deviceAddress =
                        device.address,
                    status = status
                )

            stateBridge.failed(error)

            gatt.close()

            return
        }

        when (newState) {

            BluetoothProfile.STATE_CONNECTED -> {

                stateBridge.connected()
            }

            BluetoothProfile.STATE_DISCONNECTED -> {

                stateBridge.disconnected(
                    BleDisconnectReason.ConnectionLost
                )

                gatt.close()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(
        gatt: BluetoothGatt
    ) {
        runCatching {
            gatt.disconnect()
        }

        runCatching {
            gatt.close()
        }
    }
}
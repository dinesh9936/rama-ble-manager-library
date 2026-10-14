package com.rama.blecore.internal.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.connection.BleConnectionState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal class AndroidBleConnector(
    context: Context,
    private val adapterProvider: AndroidBluetoothAdapterProvider,
    private val timeoutConfig: BleTimeoutConfig,
    private val retryStrategy: DefaultBleRetryStrategy =
        DefaultBleRetryStrategy()
) : BleConnector {

    private companion object {
        const val DISCONNECT_TIMEOUT_MS = 5_000L
    }

    private val connectionRegistry = BleConnectionRegistry()

    private val lifecycleScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private val appContext = context.applicationContext

    override suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig
    ): BleConnection {

        val address = device.address.uppercase()
        val token = connectionRegistry.reserve(address)

        var ownershipTransferred = false

        try {
            var retryAttempt = 0

            while (true) {
                try {
                    val connection = connectOnce(device, config)

                    connectionRegistry.markConnected(address, token)

                    lifecycleScope.launch {
                        try {
                            connection.state.first { state ->
                                state is BleConnectionState.Disconnected ||
                                        state is BleConnectionState.Failed
                            }

                            connection.awaitCleanup()

                        } finally {
                            try {
                                connectionRegistry.release(address, token)
                            } finally {
                                connection.markRegistryReleased()
                            }
                        }
                    }

                    ownershipTransferred = true
                    return connection

                } catch (cancellation: CancellationException) {
                    throw cancellation

                } catch (throwable: Throwable) {
                    val shouldRetry = retryStrategy.shouldRetry(
                        attempt = retryAttempt,
                        policy = config.retryPolicy
                    )

                    if (!shouldRetry) throw throwable

                    val retryDelay = retryStrategy.nextDelay(
                        attempt = retryAttempt,
                        policy = config.retryPolicy
                    )

                    retryAttempt++

                    if (retryDelay.isPositive()) {
                        delay(retryDelay)
                    }
                }
            }
        } finally {
            if (!ownershipTransferred) {
                connectionRegistry.release(address, token)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectOnce(
        device: BleDevice,
        config: BleConnectionConfig
    ): AndroidBleConnection {

        val adapter = adapterProvider.getAdapter()
            ?: throw BleException(BleError.BluetoothUnavailable())

        if (!adapter.isEnabled) {
            throw BleException(BleError.BluetoothDisabled())
        }

        val bluetoothDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            throw BleException(
                BleError.ConnectionFailed(
                    deviceAddress = device.address,
                    message = "Invalid Bluetooth device address",
                    cause = e
                )
            )
        }
        val stateBridge = ConnectionStateBridge()
        val cleanupController = GattCleanupController()

        val operationCoordinator = GattOperationCoordinator(
            onSessionInvalidated = { error ->

                lifecycleScope.launch {

                    // Stop using this GATT connection immediately.
                    // Cleanup is performed by the existing controller.
                    cleanupController.cleanup()

                    // Notify the connection state observer.
                    stateBridge.failed(error)
                }
            }
        )
        val gattMapper = GattMapper()

        val callbackHandler = GattCallbackHandler(
            operationCoordinator = operationCoordinator,
            gattMapper = gattMapper
        ) { gatt, status, newState ->

            handleConnectionStateChange(
                status = status,
                newState = newState,
                device = device,
                stateBridge = stateBridge,
                cleanupController = cleanupController,
                operationCoordinator = operationCoordinator
            )
        }

        val bluetoothGatt = try {
            bluetoothDevice.connectGatt(
                appContext,
                config.autoConnect,
                callbackHandler,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (exception: SecurityException) {
            cleanupController.completeWithoutGatt()

            throw BleException(
                BleError.PermissionDenied(
                    permissions = emptyList(),
                    message = "Bluetooth connect permission is missing"
                )
            )
        } catch (exception: Exception) {
            cleanupController.completeWithoutGatt()

            throw BleException(
                BleError.ConnectionFailed(
                    deviceAddress = device.address,
                    cause = exception
                )
            )
        }

        if (bluetoothGatt == null) {
            cleanupController.completeWithoutGatt()

            throw BleException(
                BleError.ConnectionFailed(
                    deviceAddress = device.address,
                    message = "Android returned null BluetoothGatt"
                )
            )
        }

        cleanupController.attach(bluetoothGatt)

        val gattClient = AndroidBleGattClient(
            bluetoothGatt = bluetoothGatt,
            callbackHandler = callbackHandler,
            operationCoordinator = operationCoordinator,
            timeoutConfig = timeoutConfig
        )

        val connection = AndroidBleConnection(
            device = device,
            gatt = gattClient,
            disconnectAction = {

                stateBridge.disconnecting()

                operationCoordinator.onConnectionLost(
                    deviceAddress = device.address
                )

                // Schedule the cleanup watchdog before initiating
                // the native disconnection.
                lifecycleScope.launch {

                    val cleanupCompleted = withTimeoutOrNull(
                        DISCONNECT_TIMEOUT_MS.milliseconds
                    ) {
                        cleanupController.awaitCleanup()
                        true
                    } ?: false

                    if (!cleanupCompleted) {

                        // Android did not complete local cleanup
                        // within the expected time.
                        cleanupController.cleanup()

                        stateBridge.disconnected(
                            BleDisconnectReason.ConnectionLost
                        )
                    }
                }

                try {

                    bluetoothGatt.disconnect()

                } catch (exception: Exception) {

                    // The native disconnect request failed.
                    // Close the GATT locally rather than waiting
                    // for a callback that may never arrive.
                    cleanupController.cleanup()

                    stateBridge.disconnected(
                        BleDisconnectReason.ConnectionLost
                    )
                }
            },
            cleanupCompleted = stateBridge.cleanupCompleted,
            stateBridge = stateBridge
        )

        stateBridge.attach(connection)

        // Forward controller completion to the state bridge.
        lifecycleScope.launch {
            cleanupController.awaitCleanup()
            stateBridge.markCleanupCompleted()
        }

        return try {
            val connected = withTimeout(config.connectionTimeout) {
                stateBridge.connectionDeferred.await()
            }

            config.preferredMtu?.let { mtu ->
                connected.gatt.requestMtu(mtu)
            }

            connected

        } catch (timeout: TimeoutCancellationException) {

            cleanupFailedConnection(
                cleanupController = cleanupController,
                stateBridge = stateBridge
            )

            stateBridge.failed(
                BleError.Timeout(
                    operation = "connect:${device.address}",
                    message = "BLE connection timed out"
                )
            )

            throw BleException(
                BleError.Timeout(
                    operation = "connect:${device.address}",
                    message = "BLE connection timed out"
                )
            )

        } catch (cancellation: CancellationException) {

            cleanupFailedConnection(
                cleanupController = cleanupController,
                stateBridge = stateBridge
            )

            stateBridge.failed(
                BleError.ConnectionFailed(
                    deviceAddress = device.address,
                    message = "Connection attempt cancelled"
                )
            )

            throw cancellation

        } catch (exception: Exception) {

            cleanupFailedConnection(
                cleanupController = cleanupController,
                stateBridge = stateBridge
            )

            val error = if (exception is BleException) {
                exception
            } else {
                BleException(
                    BleError.ConnectionFailed(
                        deviceAddress = device.address,
                        message = "BLE connection establishment failed",
                        cause = exception
                    )
                )
            }

            stateBridge.failed(error.error)

            throw error
        }
    }



    private fun handleConnectionStateChange(
        status: Int,
        newState: Int,
        device: BleDevice,
        stateBridge: ConnectionStateBridge,
        cleanupController: GattCleanupController,
        operationCoordinator: GattOperationCoordinator
    ) {

        if (status != BluetoothGatt.GATT_SUCCESS) {

            operationCoordinator.onConnectionLost(
                deviceAddress = device.address
            )

            if (cleanupController.isCleanupRequested()) return

            val error = BleError.ConnectionFailed(
                deviceAddress = device.address,
                status = status
            )

            cleanupController.cleanup()
            stateBridge.failed(error)

            return
        }

        when (newState) {

            BluetoothProfile.STATE_CONNECTED -> {

                if (!cleanupController.isCleanupRequested()) {
                    stateBridge.connected()
                }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {

                operationCoordinator.onConnectionLost(
                    deviceAddress = device.address
                )

                if (cleanupController.isCleanupRequested()) return

                cleanupController.cleanup()

                stateBridge.disconnected(
                    BleDisconnectReason.ConnectionLost
                )
            }
        }
    }

    private suspend fun cleanupFailedConnection(
        cleanupController: GattCleanupController,
        stateBridge: ConnectionStateBridge
    ) {
        withContext(NonCancellable) {
            cleanupController.cleanup()

            // Wait until the local close attempt has finished.
            // The controller supports cleanup requests made
            // before GATT attachment.
            cleanupController.awaitCleanup()

            stateBridge.markCleanupCompleted()
        }
    }
}
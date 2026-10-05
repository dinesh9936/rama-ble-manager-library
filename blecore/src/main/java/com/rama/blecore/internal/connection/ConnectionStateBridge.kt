package com.rama.blecore.internal.connection

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import kotlinx.coroutines.CompletableDeferred

internal class ConnectionStateBridge {

    private var connection: AndroidBleConnection? = null

    private var pendingConnected = false

    private var pendingFailure: BleError? = null

    val connectionDeferred =
        CompletableDeferred<AndroidBleConnection>()

    @Synchronized
    fun attach(
        connection: AndroidBleConnection
    ) {
        this.connection = connection

        pendingFailure?.let { error ->
            connection.markFailed(error)

            if (!connectionDeferred.isCompleted) {
                connectionDeferred.completeExceptionally(
                    BleException(error)
                )
            }

            return
        }

        if (pendingConnected) {
            connection.markConnected()

            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(connection)
            }
        }
    }

    @Synchronized
    fun connected() {

        val currentConnection =
            connection

        if (currentConnection == null) {
            pendingConnected = true
            return
        }

        currentConnection.markConnected()

        if (!connectionDeferred.isCompleted) {
            connectionDeferred.complete(
                currentConnection
            )
        }
    }

    @Synchronized
    fun disconnected(
        reason: com.rama.blecore.connection.BleDisconnectReason
    ) {
        connection?.markDisconnected(reason)
    }

    @Synchronized
    fun failed(
        error: BleError
    ) {

        val currentConnection =
            connection

        if (currentConnection == null) {
            pendingFailure = error
        } else {
            currentConnection.markFailed(error)
        }

        if (!connectionDeferred.isCompleted) {
            connectionDeferred.completeExceptionally(
                BleException(error)
            )
        }
    }
}
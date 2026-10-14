package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.error.BleError

/**
 * Maps Android GATT connection errors based on
 * whether the connection was established previously.
 */
internal object GattConnectionErrorMapper {

    fun map(
        deviceAddress: String,
        status: Int,
        wasConnected: Boolean
    ): Result {

        return if (wasConnected) {
            Result.Disconnected(
                BleDisconnectReason.GattError(
                    status = status
                )
            )
        } else {
            Result.Failed(
                BleError.ConnectionFailed(
                    deviceAddress = deviceAddress,
                    status = status,
                    message = "BLE connection failed with GATT status $status"
                )
            )
        }
    }

    sealed interface Result {

        data class Failed(
            val error: BleError.ConnectionFailed
        ) : Result

        data class Disconnected(
            val reason: BleDisconnectReason.GattError
        ) : Result
    }
}
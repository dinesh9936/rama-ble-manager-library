package com.rama.blecore.connection

sealed interface BleDisconnectReason {

    data object RequestedByUser : BleDisconnectReason

    data object ConnectionLost : BleDisconnectReason

    data object Timeout : BleDisconnectReason

    data class GattError(
        val status: Int
    ) : BleDisconnectReason

    data class Unknown(
        val status: Int? = null
    ) : BleDisconnectReason
}
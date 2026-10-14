package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleDisconnectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GattConnectionErrorMapperTest {

    private val deviceAddress = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `initial connection failure preserves GATT status`() {

        val result = GattConnectionErrorMapper.map(
            deviceAddress = deviceAddress,
            status = 133,
            wasConnected = false
        )

        assertTrue(
            result is GattConnectionErrorMapper.Result.Failed
        )

        val failure =
            result as GattConnectionErrorMapper.Result.Failed

        assertEquals(
            deviceAddress,
            failure.error.deviceAddress
        )

        assertEquals(
            133,
            failure.error.status
        )
    }

    @Test
    fun `unexpected disconnection preserves GATT status`() {

        val result = GattConnectionErrorMapper.map(
            deviceAddress = deviceAddress,
            status = 8,
            wasConnected = true
        )

        assertTrue(
            result is GattConnectionErrorMapper.Result.Disconnected
        )

        val disconnected =
            result as GattConnectionErrorMapper.Result.Disconnected

        assertEquals(
            BleDisconnectReason.GattError(8),
            disconnected.reason
        )
    }
}
package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleDisconnectReason
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import org.mockito.Mockito.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Test

class ConnectionStateBridgeTest {

    @Test
    fun `manual disconnection publishes RequestedByUser to StateFlow`() {

        val bridge = ConnectionStateBridge()

        val device = BleDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Test Device"
        )

        val gattClient = mock(BleGattClient::class.java)

        val connection = AndroidBleConnection(
            device = device,
            gatt = gattClient,
            disconnectAction = {},
            cleanupCompleted = bridge.cleanupCompleted,
            stateBridge = bridge
        )

        // Attach connection to lifecycle bridge.
        bridge.attach(connection)

        // Simulate a successful connection.
        bridge.connected()

        assertTrue(
            connection.state.value is BleConnectionState.Connected
        )

        // Simulate application requesting disconnection.
        bridge.disconnecting()

        // Complete the user-requested disconnection.
        bridge.completeRequestedDisconnect()

        // Verify published state.
        val state = connection.state.value

        assertTrue(
            state is BleConnectionState.Disconnected
        )

        val disconnected =
            state as BleConnectionState.Disconnected

        assertEquals(
            BleDisconnectReason.RequestedByUser,
            disconnected.reason
        )

        // Verify terminal lifecycle.
        assertTrue(bridge.isTerminal())
    }

    @Test
    fun `manual disconnect reaches terminal state and ignores late GATT errors`() {

        val bridge = ConnectionStateBridge()
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // Simulate successful connection.
        bridge.connected()

        assertTrue(bridge.isConnected())
        assertTrue(bridge.wasConnected())

        // Application requests disconnect.
        bridge.disconnecting()

        assertFalse(bridge.isConnected())
        assertFalse(bridge.isTerminal())

        // Android confirms disconnect, or watchdog finishes.
        bridge.completeRequestedDisconnect()

        // Connection should now be terminal.
        assertTrue(bridge.isTerminal())
        assertFalse(bridge.isConnected())

        // Preserve connection history.
        assertTrue(bridge.wasConnected())

        // A late GATT error must not overwrite the result.
        val handled = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 133
        )

        assertFalse(handled)
        assertTrue(bridge.isTerminal())
    }

    @Test
    fun `new connection has never been connected`() {
        val bridge = ConnectionStateBridge()

        assertFalse(bridge.isConnected())
        assertFalse(bridge.wasConnected())
    }

    @Test
    fun `connection remembers successful establishment after disconnection`() {

        val bridge = ConnectionStateBridge()

        // Initial state: CONNECTING
        assertFalse(bridge.isConnected())
        assertFalse(bridge.wasConnected())

        // Simulate Android reporting STATE_CONNECTED
        bridge.connected()

        // Connection established
        assertTrue(bridge.isConnected())
        assertTrue(bridge.wasConnected())

        // Simulate unexpected disconnection
        bridge.disconnected(
            BleDisconnectReason.ConnectionLost
        )

        // Connection is no longer active
        assertFalse(bridge.isConnected())

        // But it was successfully connected previously
        assertTrue(bridge.wasConnected())

        assertTrue(bridge.isTerminal())
    }

    @Test
    fun `connection failure before establishment never marks device connected`() {

        val bridge = ConnectionStateBridge()

        val error = BleError.ConnectionFailed(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            status = 133,
            message = "BLE connection failed with GATT status 133"
        )

        // Initial state
        assertFalse(bridge.isConnected())
        assertFalse(bridge.wasConnected())

        // Simulate Android reporting a connection failure
        bridge.failed(error)

        // Connection was never established
        assertFalse(bridge.isConnected())
        assertFalse(bridge.wasConnected())

        // Connection lifecycle is terminal
        assertTrue(bridge.isTerminal())

        // Original GATT status is preserved
        assertEquals(133, error.status)
    }

    @Test
    fun `late connected callback does not restore failed connection`() {

        val bridge = ConnectionStateBridge()

        val error = BleError.ConnectionFailed(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            status = 133
        )

        // Initial connection fails
        bridge.failed(error)

        assertTrue(bridge.isTerminal())
        assertFalse(bridge.wasConnected())

        // Android delivers a late STATE_CONNECTED callback
        bridge.connected()

        // Failed connection must not become active
        assertFalse(bridge.isConnected())
        assertFalse(bridge.wasConnected())
        assertTrue(bridge.isTerminal())
    }

    @Test
    fun `GATT error is handled only once after connection failure`() {

        val bridge = ConnectionStateBridge()
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // First GATT error during connection establishment
        val firstHandled = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 133
        )

        assertTrue(firstHandled)
        assertTrue(bridge.isTerminal())
        assertFalse(bridge.wasConnected())

        // Another callback arrives after the terminal state
        val secondHandled = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 8
        )

        // Terminal state must not be overwritten
        assertFalse(secondHandled)

        // The original failure must be preserved
        assertTrue(
            bridge.connectionDeferred.isCompleted
        )
    }

    @Test
    fun `connection failure preserves GATT status in exception`() = runBlocking {

        val bridge = ConnectionStateBridge()
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // Simulate a GATT error during connection establishment.
        val handled = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 133
        )

        assertTrue(handled)

        // Retrieve the exception delivered to the connection caller.
        val exception = try {
            bridge.connectionDeferred.await()
            throw AssertionError("Expected BleException")
        } catch (e: BleException) {
            e
        }

        // Verify the exact error type.
        val error = exception.error

        assertTrue(error is BleError.ConnectionFailed)

        val connectionError = error as BleError.ConnectionFailed

        // Verify that Android's original GATT status is preserved.
        assertEquals(133, connectionError.status)

        // Verify the device address is preserved.
        assertEquals(deviceAddress, connectionError.deviceAddress)

        // Connection must remain terminal.
        assertTrue(bridge.isTerminal())
        assertFalse(bridge.wasConnected())
    }

    @Test
    fun `GATT error after successful connection is treated as disconnection`() {

        val bridge = ConnectionStateBridge()
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        // Simulate successful BLE connection.
        bridge.connected()

        assertTrue(bridge.isConnected())
        assertTrue(bridge.wasConnected())

        // Android reports GATT status 8.
        val handled = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 8
        )

        assertTrue(handled)

        // Connection should no longer be active.
        assertFalse(bridge.isConnected())

        // Connection was previously established.
        assertTrue(bridge.wasConnected())

        // Lifecycle should be terminal.
        assertTrue(bridge.isTerminal())

        // A late error must not change the terminal state.
        val handledAgain = bridge.handleGattError(
            deviceAddress = deviceAddress,
            status = 133
        )

        assertFalse(handledAgain)
    }

    @Test
    fun `unexpected disconnection publishes GATT error to StateFlow`() {

        val bridge = ConnectionStateBridge()

        val device = BleDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Test Device"
        )

        // Mock the GATT client because this test
        // does not perform real Bluetooth operations.
        val gattClient = mock(BleGattClient::class.java)

        val connection = AndroidBleConnection(
            device = device,
            gatt = gattClient,
            disconnectAction = {},
            cleanupCompleted = bridge.cleanupCompleted,
            stateBridge = bridge
        )

        // Attach connection to lifecycle bridge.
        bridge.attach(connection)

        // Simulate a successful connection.
        bridge.connected()

        assertTrue(
            connection.state.value is BleConnectionState.Connected
        )

        // Simulate unexpected GATT disconnection.
        val handled = bridge.handleGattError(
            deviceAddress = device.address,
            status = 8
        )

        assertTrue(handled)

        // Verify the state visible to the application.
        val state = connection.state.value

        assertTrue(
            state is BleConnectionState.Disconnected
        )

        val disconnected =
            state as BleConnectionState.Disconnected

        assertEquals(
            BleDisconnectReason.GattError(8),
            disconnected.reason
        )

        // Verify the connection remains terminal.
        assertTrue(bridge.isTerminal())
    }


    @Test
    fun `concurrent GATT error and manual disconnect produce one terminal state`() =
        runBlocking {

            repeat(100) {

                val bridge = ConnectionStateBridge()

                bridge.connected()

                val start = CompletableDeferred<Unit>()

                val gattErrorJob = async(Dispatchers.Default) {
                    start.await()

                    bridge.handleGattError(
                        deviceAddress = "AA:BB:CC:DD:EE:FF",
                        status = 8
                    )
                }

                val disconnectJob = async(Dispatchers.Default) {
                    start.await()

                    bridge.disconnecting()
                    bridge.completeRequestedDisconnect()
                }

                start.complete(Unit)

                awaitAll(gattErrorJob, disconnectJob)

                assertTrue(bridge.isTerminal())
                assertFalse(bridge.isConnected())
                assertTrue(bridge.wasConnected())

                // Once terminal, another GATT error must be ignored.
                assertFalse(
                    bridge.handleGattError(
                        deviceAddress = "AA:BB:CC:DD:EE:FF",
                        status = 133
                    )
                )
            }
        }
}
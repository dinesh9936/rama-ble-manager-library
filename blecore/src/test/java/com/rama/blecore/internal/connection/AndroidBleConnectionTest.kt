package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicInteger

class AndroidBleConnectionTest {

    private val device = mock(BleDevice::class.java)
    private val gatt = mock(BleGattClient::class.java)

    private fun createConnection(
        disconnectAction: suspend () -> Unit,
        cleanupCompleted: CompletableDeferred<Unit> =
            CompletableDeferred()
    ): AndroidBleConnection {

        return AndroidBleConnection(
            device = device,
            gatt = gatt,
            disconnectAction = disconnectAction,
            cleanupCompleted = cleanupCompleted,
            stateBridge = ConnectionStateBridge()
        )
    }

    @Test
    fun `disconnect invokes action once`() = runBlocking {

        val calls = AtomicInteger(0)

        val connection = createConnection(
            disconnectAction = {
                calls.incrementAndGet()
            }
        )

        connection.disconnect()
        connection.disconnect()

        assertEquals(1, calls.get())
        assertTrue(
            connection.state.value is BleConnectionState.Disconnecting
        )
    }

    @Test
    fun `concurrent disconnect calls invoke action once`() = runBlocking {

        val calls = AtomicInteger(0)

        val connection = createConnection(
            disconnectAction = {
                calls.incrementAndGet()
            }
        )

        (1..20).map {
            async {
                connection.disconnect()
            }
        }.awaitAll()

        assertEquals(1, calls.get())
    }

    @Test
    fun `disconnect failure permits retry`() = runBlocking {

        val calls = AtomicInteger(0)

        val connection = createConnection(
            disconnectAction = {
                if (calls.incrementAndGet() == 1) {
                    throw IllegalStateException("Disconnect failed")
                }
            }
        )

        try {
            connection.disconnect()
        } catch (_: IllegalStateException) {
            // Expected first failure.
        }

        connection.disconnect()

        assertEquals(2, calls.get())
    }

    @Test
    fun `disconnect cancellation permits retry`() = runBlocking {

        val calls = AtomicInteger(0)

        val connection = createConnection(
            disconnectAction = {
                if (calls.incrementAndGet() == 1) {
                    throw CancellationException("Disconnect cancelled")
                }
            }
        )

        try {
            connection.disconnect()
        } catch (_: CancellationException) {
            // Expected first failure.
        }

        connection.disconnect()

        assertEquals(2, calls.get())
    }

    @Test
    fun `cleanup completion is independent of disconnect request`() = runBlocking {

        val cleanupCompleted = CompletableDeferred<Unit>()

        val connection = createConnection(
            disconnectAction = {},
            cleanupCompleted = cleanupCompleted
        )

        connection.disconnect()

        assertFalse(cleanupCompleted.isCompleted)

        cleanupCompleted.complete(Unit)

        connection.awaitCleanup()

        assertTrue(cleanupCompleted.isCompleted)
    }
}
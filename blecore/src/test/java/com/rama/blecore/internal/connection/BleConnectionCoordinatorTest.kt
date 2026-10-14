package com.rama.blecore.internal.connection

import com.rama.blecore.connection.BleConnection
import com.rama.blecore.connection.BleConnectionConfig
import com.rama.blecore.connection.BleConnectionPolicy
import com.rama.blecore.connection.BleConnectionState
import com.rama.blecore.connection.BleConnector
import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import com.rama.blecore.gatt.BleGattClient
import com.rama.blecore.model.BleDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.awaitAll


class BleConnectionCoordinatorTest {

    private val deviceA = BleDevice(
        address = "AA:BB:CC:DD:EE:01",
        name = "Device A"
    )

    private val deviceB = BleDevice(
        address = "AA:BB:CC:DD:EE:02",
        name = "Device B"
    )

    private val deviceC = BleDevice(
        address = "AA:BB:CC:DD:EE:03",
        name = "Device C"
    )

    @Test
    fun `multi device policy supports concurrent connections`() = runBlocking {

        val fakeConnector = FakeBleConnector()

        val coordinator = BleConnectionCoordinator(
            delegate = fakeConnector,
            connectionPolicy = BleConnectionPolicy.MULTI_DEVICE
        )

        val devices = listOf(
            deviceA,
            deviceB,
            deviceC
        )

        val connections = devices.map { device ->
            async {
                coordinator.connect(
                    device = device,
                    config = BleConnectionConfig()
                )
            }
        }.awaitAll()

        assertEquals(3, connections.size)

        assertEquals(
            devices.map { it.address }.toSet(),
            connections.map { it.device.address }.toSet()
        )

        connections.forEach { connection ->
            assertTrue(
                connection.state.value is BleConnectionState.Connected
            )
        }
    }


    @Test
    fun `cancelling connection switch releases mutex and prevents new connection`() =
        runBlocking {

            val fakeConnector = FakeBleConnector(
                autoCompleteCleanup = false
            )

            val coordinator = BleConnectionCoordinator(
                delegate = fakeConnector,
                connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE,
                disconnectTimeoutMs = 5_000L
            )

            // Step 1: Connect Device A.
            val connectionA = coordinator.connect(
                device = deviceA,
                config = BleConnectionConfig()
            ) as FakeBleConnection

            // Step 2: Start connecting Device B.
            val connectB = async {
                coordinator.connect(
                    device = deviceB,
                    config = BleConnectionConfig()
                )
            }

            // Wait until Device A receives a disconnect request.
            withTimeout(1_000L.milliseconds) {
                connectionA.disconnectRequested.await()
            }

            assertEquals(1, connectionA.disconnectCount)

            // Device B must still be waiting.
            assertFalse(connectB.isCompleted)

            // Step 3: Cancel the connection request.
            connectB.cancel()

            // Wait until cancellation finishes.
            connectB.join()

            assertTrue(connectB.isCancelled)

            // Device B must never have reached the connector.
            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Step 4: Complete the previous connection's cleanup.
            connectionA.completeCleanup()
            connectionA.completeRegistryRelease()

            // Step 5: Verify another request can acquire the mutex.
            val connectionC = withTimeout(1_000L.milliseconds) {
                coordinator.connect(
                    device = deviceC,
                    config = BleConnectionConfig()
                )
            }

            assertEquals(deviceC, connectionC.device)

            assertEquals(
                listOf(deviceA.address, deviceC.address),
                fakeConnector.connectedAddresses
            )
        }

    @Test
    fun `single device policy serializes concurrent connection requests`() =
        runBlocking {

            val fakeConnector = FakeBleConnector(
                autoCompleteCleanup = false
            )

            val coordinator = BleConnectionCoordinator(
                delegate = fakeConnector,
                connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE,
                disconnectTimeoutMs = 5_000L
            )

            // Step 1: Connect A.
            val connectionA = coordinator.connect(
                device = deviceA,
                config = BleConnectionConfig()
            ) as FakeBleConnection

            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Step 2: Request B and C concurrently.
            val connectB = async {
                coordinator.connect(
                    device = deviceB,
                    config = BleConnectionConfig()
                )
            }

            // Ensure B has acquired the mutex and requested A's disconnect.
            withTimeout(1_000L.milliseconds) {
                connectionA.disconnectRequested.await()
            }

            val connectC = async {
                coordinator.connect(
                    device = deviceC,
                    config = BleConnectionConfig()
                )
            }

            // Neither B nor C should connect before A's cleanup.
            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            assertFalse(connectB.isCompleted)
            assertFalse(connectC.isCompleted)

            // Step 3: Finish A's cleanup.
            connectionA.completeCleanup()
            connectionA.completeRegistryRelease()

            // B should now connect.
            val connectionB = withTimeout(1_000L.milliseconds) {
                connectB.await()
            } as FakeBleConnection

            assertEquals(deviceB, connectionB.device)

            // Step 4: C must disconnect B first.
            withTimeout(1_000L.milliseconds) {
                connectionB.disconnectRequested.await()
            }

            assertEquals(
                listOf(deviceA.address, deviceB.address),
                fakeConnector.connectedAddresses
            )

            assertFalse(connectC.isCompleted)

            // Step 5: Finish B's cleanup.
            connectionB.completeCleanup()
            connectionB.completeRegistryRelease()

            // C can now connect.
            val connectionC = withTimeout(1_000L.milliseconds) {
                connectC.await()
            }

            assertEquals(deviceC, connectionC.device)

            assertEquals(
                listOf(
                    deviceA.address,
                    deviceB.address,
                    deviceC.address
                ),
                fakeConnector.connectedAddresses
            )

            assertEquals(1, connectionA.disconnectCount)
            assertEquals(1, connectionB.disconnectCount)
        }

    @Test
    fun `single device policy does not connect next device when cleanup times out`() =
        runBlocking {

            // Arrange
            val fakeConnector = FakeBleConnector(
                autoCompleteCleanup = false
            )

            val coordinator = BleConnectionCoordinator(
                delegate = fakeConnector,
                connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE,
                disconnectTimeoutMs = 100L
            )

            // Connect Device A.
            val connectionA = coordinator.connect(
                device = deviceA,
                config = BleConnectionConfig()
            ) as FakeBleConnection

            assertTrue(
                connectionA.state.value is BleConnectionState.Connected
            )

            // Act: Attempt to connect Device B.
            val exception = try {
                coordinator.connect(
                    device = deviceB,
                    config = BleConnectionConfig()
                )

                fail("Expected cleanup timeout")
                null

            } catch (e: BleException) {
                e
            }

            // Assert: Coordinator returns a connection failure.
            assertTrue(
                exception?.error is BleError.ConnectionFailed
            )

            // Device A received a disconnect request.
            assertEquals(
                1,
                connectionA.disconnectCount
            )

            // Device B was never connected.
            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Cleanup is intentionally incomplete.
            assertFalse(
                connectionA.isCleanupCompleted()
            )
        }

    @Test
    fun `single device waits for cleanup and registry release before connecting next device`() =
        runBlocking {

            val fakeConnector = FakeBleConnector(
                autoCompleteCleanup = false
            )

            val coordinator = BleConnectionCoordinator(
                delegate = fakeConnector,
                connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE
            )

            // Connect first device.
            val connectionA = coordinator.connect(
                device = deviceA,
                config = BleConnectionConfig()
            ) as FakeBleConnection

            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Request connection to Device B concurrently.
            val connectB = async {
                coordinator.connect(
                    device = deviceB,
                    config = BleConnectionConfig()
                )
            }

            // Wait until Device A receives the disconnect request.
            withTimeout(1_000L.milliseconds) {
                connectionA.disconnectRequested.await()
            }

            assertEquals(1, connectionA.disconnectCount)

            // B must not connect while A's cleanup is pending.
            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Simulate Android finishing GATT cleanup.
            connectionA.completeCleanup()

            // Still waiting for registry release.
            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )

            // Simulate registry ownership being released.
            connectionA.completeRegistryRelease()

            // B can now connect.
            val connectionB = withTimeout(1_000L.milliseconds) {
                connectB.await()
            }

            assertEquals(deviceB, connectionB.device)

            assertEquals(
                listOf(deviceA.address, deviceB.address),
                fakeConnector.connectedAddresses
            )
        }

    @Test
    fun `single device policy connects first device`() = runBlocking {

        val fakeConnector = FakeBleConnector()

        val coordinator = BleConnectionCoordinator(
            delegate = fakeConnector,
            connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE
        )

        val connection = coordinator.connect(
            device = deviceA,
            config = BleConnectionConfig()
        )

        assertEquals(deviceA, connection.device)

        assertEquals(
            listOf(deviceA.address),
            fakeConnector.connectedAddresses
        )

        assertTrue(
            connection.state.value is BleConnectionState.Connected
        )
    }

    @Test
    fun `single device policy rejects duplicate without disconnecting`() =
        runBlocking {

            val fakeConnector = FakeBleConnector()

            val coordinator = BleConnectionCoordinator(
                delegate = fakeConnector,
                connectionPolicy = BleConnectionPolicy.SINGLE_DEVICE
            )

            val firstConnection = coordinator.connect(
                device = deviceA,
                config = BleConnectionConfig()
            ) as FakeBleConnection

            try {
                coordinator.connect(
                    device = deviceA,
                    config = BleConnectionConfig()
                )

                fail("Expected BleException for duplicate connection")

            } catch (exception: BleException) {
                assertTrue(
                    exception.error is BleError.AlreadyConnected
                )
            }

            assertEquals(0, firstConnection.disconnectCount)

            assertTrue(
                firstConnection.state.value is BleConnectionState.Connected
            )

            assertEquals(
                listOf(deviceA.address),
                fakeConnector.connectedAddresses
            )
        }

    @Test
    fun `multi device policy allows different devices`() = runBlocking {

        val fakeConnector = FakeBleConnector()

        val coordinator = BleConnectionCoordinator(
            delegate = fakeConnector,
            connectionPolicy = BleConnectionPolicy.MULTI_DEVICE
        )

        val connectionA = coordinator.connect(
            device = deviceA,
            config = BleConnectionConfig()
        )

        val connectionB = coordinator.connect(
            device = deviceB,
            config = BleConnectionConfig()
        )

        assertEquals(deviceA, connectionA.device)
        assertEquals(deviceB, connectionB.device)

        assertTrue(
            connectionA.state.value is BleConnectionState.Connected
        )

        assertTrue(
            connectionB.state.value is BleConnectionState.Connected
        )

        assertEquals(
            listOf(deviceA.address, deviceB.address),
            fakeConnector.connectedAddresses
        )
    }

    /**
     * Fake low-level connector.
     *
     * Records connection requests and returns fake connections.
     * No Android Bluetooth hardware is required.
     */
    private class FakeBleConnector(
        private val autoCompleteCleanup: Boolean = true
    ) : BleConnector {

        val connectedAddresses = mutableListOf<String>()

        override suspend fun connect(
            device: BleDevice,
            config: BleConnectionConfig
        ): BleConnection {

            connectedAddresses.add(device.address)

            return FakeBleConnection(
                device = device,
                autoCompleteCleanup = autoCompleteCleanup
            )
        }
    }

    /**
     * Implements the internal lifecycle contract required by
     * BleConnectionCoordinator.
     */
    private class FakeBleConnection(
        override val device: BleDevice,
        private val autoCompleteCleanup: Boolean = true
    ) : ManagedBleConnection {

        private val mutableState =
            MutableStateFlow<BleConnectionState>(
                BleConnectionState.Connected(device)
            )

        override val state = mutableState

        override val gatt: BleGattClient = createFakeGattClient()

        private val cleanupCompleted = CompletableDeferred<Unit>()

        private val registryReleased = CompletableDeferred<Unit>()

        val disconnectRequested = CompletableDeferred<Unit>()

        var disconnectCount = 0
            private set

        override suspend fun disconnect() {
            disconnectCount++

            mutableState.value = BleConnectionState.Disconnected(
                device = device,
                reason = null
            )

            disconnectRequested.complete(Unit)

            if (autoCompleteCleanup) {
                completeCleanup()
                completeRegistryRelease()
            }
        }

        fun isCleanupCompleted(): Boolean {
            return cleanupCompleted.isCompleted
        }

        fun completeCleanup() {
            cleanupCompleted.complete(Unit)
        }

        fun completeRegistryRelease() {
            registryReleased.complete(Unit)
        }

        override suspend fun awaitCleanup() {
            cleanupCompleted.await()
        }

        override suspend fun awaitRegistryRelease() {
            registryReleased.await()
        }
    }

    companion object {

        /**
         * Creates an interface-only test double.
         *
         * GATT operations are intentionally unsupported because
         * these tests only exercise connection coordination.
         */
        private fun createFakeGattClient(): BleGattClient {

            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(
                BleGattClient::class.java.classLoader,
                arrayOf(BleGattClient::class.java)
            ) { _, method, _ ->

                when (method.name) {
                    "toString" -> "FakeBleGattClient"
                    "hashCode" -> 0
                    "equals" -> false

                    else -> throw UnsupportedOperationException(
                        "Unexpected GATT operation: ${method.name}"
                    )
                }

            } as BleGattClient
        }
    }
}
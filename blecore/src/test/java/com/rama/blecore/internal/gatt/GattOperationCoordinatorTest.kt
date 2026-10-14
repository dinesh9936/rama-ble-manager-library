package com.rama.blecore.internal.gatt

import com.rama.blecore.error.BleException
import com.rama.blecore.gatt.BleGattEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GattOperationCoordinatorTest {

    private val deviceAddress = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `disconnect before execute prevents starter`() = runBlocking {

        val coordinator = GattOperationCoordinator()
        val starterCalls = AtomicInteger(0)

        coordinator.onConnectionLost(deviceAddress)

        val result = runCatching {
            coordinator.execute(
                timeout = 1.seconds,
                matcher = { _: com.rama.blecore.gatt.BleGattEvent ->
                    "result"
                },
                starter = {
                    starterCalls.incrementAndGet()
                    true
                }
            )
        }

        assertTrue(result.exceptionOrNull() is BleException)
        assertEquals(0, starterCalls.get())
    }

    @Test
    fun `disconnect fails pending operation promptly`() = runBlocking {

        val coordinator = GattOperationCoordinator()

        val started = CompletableDeferred<Unit>()

        val operation = async {

            runCatching {

                coordinator.execute<String>(
                    timeout = 10.seconds,
                    matcher = { _: BleGattEvent ->
                        "result"
                    },
                    starter = {
                        started.complete(Unit)
                        true
                    }
                )
            }
        }

        started.await()

        coordinator.onConnectionLost(deviceAddress)

        val result = withTimeout(1.seconds) {
            operation.await()
        }

        assertTrue(
            result.exceptionOrNull() is BleException
        )
    }

    @Test
    fun `disconnect rejects queued operation`() = runBlocking {

        val coordinator = GattOperationCoordinator()

        val firstStarted = CompletableDeferred<Unit>()

        val secondStarterCalls = AtomicInteger(0)

        val first = async {

            runCatching {

                coordinator.execute<String>(
                    timeout = 10.seconds,
                    matcher = { _: BleGattEvent ->
                        "first"
                    },
                    starter = {
                        firstStarted.complete(Unit)
                        true
                    }
                )
            }
        }

        firstStarted.await()

        val second = async {

            runCatching {

                coordinator.execute<String>(
                    timeout = 10.seconds,
                    matcher = { _: BleGattEvent ->
                        "second"
                    },
                    starter = {
                        secondStarterCalls.incrementAndGet()
                        true
                    }
                )
            }
        }

        coordinator.onConnectionLost(deviceAddress)

        val firstResult = withTimeout(1.seconds) {
            first.await()
        }

        val secondResult = withTimeout(1.seconds) {
            second.await()
        }

        assertTrue(
            firstResult.exceptionOrNull() is BleException
        )

        assertTrue(
            secondResult.exceptionOrNull() is BleException
        )

        assertEquals(
            0,
            secondStarterCalls.get()
        )
    }


    @Test
    fun `cancellation invalidates active GATT session`() = runBlocking {

        val coordinator = GattOperationCoordinator()

        val firstStarted = CompletableDeferred<Unit>()

        val first = launch {

            coordinator.execute<String>(
                timeout = 10.seconds,
                matcher = { _: BleGattEvent -> "first" },
                starter = {
                    firstStarted.complete(Unit)
                    true
                }
            )
        }

        firstStarted.await()

        first.cancelAndJoin()

        val secondStarterCalls = AtomicInteger(0)

        val secondResult = runCatching {
            coordinator.execute<String>(
                timeout = 1.seconds,
                matcher = { _: BleGattEvent -> "second" },
                starter = {
                    secondStarterCalls.incrementAndGet()
                    true
                }
            )
        }

        assertTrue(
            secondResult.exceptionOrNull() is BleException
        )

        assertEquals(
            0,
            secondStarterCalls.get()
        )
    }


    @Test
    fun `timeout invalidates GATT session and rejects subsequent operations`() =
        runBlocking {

            val coordinator = GattOperationCoordinator()

            val firstResult = runCatching {
                coordinator.execute<String>(
                    timeout = 100.milliseconds,
                    matcher = { _: BleGattEvent -> "first" },
                    starter = { true }
                )
            }

            assertTrue(
                firstResult.exceptionOrNull() is TimeoutCancellationException
            )

            val secondStarterCalls = AtomicInteger(0)

            val secondResult = runCatching {
                coordinator.execute<String>(
                    timeout = 1.seconds,
                    matcher = { _: BleGattEvent -> "second" },
                    starter = {
                        secondStarterCalls.incrementAndGet()
                        true
                    }
                )
            }

            assertTrue(
                secondResult.exceptionOrNull() is BleException
            )

            assertEquals(
                0,
                secondStarterCalls.get()
            )
        }

    @Test
    fun `timeout invalidates session exactly once`() = runBlocking {

        val invalidationCount = AtomicInteger(0)

        val coordinator = GattOperationCoordinator(
            onSessionInvalidated = {
                invalidationCount.incrementAndGet()
            }
        )

        val firstResult = runCatching {
            coordinator.execute<String>(
                timeout = 100.milliseconds,
                matcher = { _: BleGattEvent -> "result" },
                starter = { true }
            )
        }

        assertTrue(
            firstResult.exceptionOrNull() is TimeoutCancellationException
        )

        assertEquals(1, invalidationCount.get())

        val secondStarterCalls = AtomicInteger(0)

        val secondResult = runCatching {
            coordinator.execute<String>(
                timeout = 1.seconds,
                matcher = { _: BleGattEvent -> "result" },
                starter = {
                    secondStarterCalls.incrementAndGet()
                    true
                }
            )
        }

        assertTrue(
            secondResult.exceptionOrNull() is BleException
        )

        assertEquals(0, secondStarterCalls.get())

        assertEquals(1, invalidationCount.get())
    }

    @Test
    fun `cancellation invalidates session exactly once`() = runBlocking {

        val invalidationCount = AtomicInteger(0)
        val operationStarted = CompletableDeferred<Unit>()

        val coordinator = GattOperationCoordinator(
            onSessionInvalidated = {
                invalidationCount.incrementAndGet()
            }
        )

        val operation = launch {
            coordinator.execute<String>(
                timeout = 10.seconds,
                matcher = { _: BleGattEvent -> "result" },
                starter = {
                    operationStarted.complete(Unit)
                    true
                }
            )
        }

        operationStarted.await()

        operation.cancelAndJoin()

        assertEquals(1, invalidationCount.get())

        val secondResult = runCatching {
            coordinator.execute<String>(
                timeout = 1.seconds,
                matcher = { _: BleGattEvent -> "result" },
                starter = { true }
            )
        }

        assertTrue(
            secondResult.exceptionOrNull() is BleException
        )

        assertEquals(1, invalidationCount.get())
    }

    @Test
    fun `native start failure returns BleException without invalidating session`() =
        runBlocking {

            val invalidationCount = AtomicInteger(0)

            val coordinator = GattOperationCoordinator(
                onSessionInvalidated = {
                    invalidationCount.incrementAndGet()
                }
            )

            val result = runCatching {
                coordinator.execute<String>(
                    timeout = 1.seconds,
                    matcher = { _: BleGattEvent -> "result" },
                    starter = { false }
                )
            }

            assertTrue(result.exceptionOrNull() is BleException)
            assertEquals(0, invalidationCount.get())
        }
    @Test
    fun `cancelled coroutine does not start native GATT operation`() = runBlocking {

        val coordinator = GattOperationCoordinator()

        val starterCalls = AtomicInteger(0)

        val job = launch(start = CoroutineStart.LAZY) {

            coordinator.execute<String>(
                timeout = 10.seconds,
                matcher = { _: BleGattEvent -> "result" },
                starter = {
                    starterCalls.incrementAndGet()
                    true
                }
            )
        }

        job.cancelAndJoin()

        assertEquals(0, starterCalls.get())
    }
}
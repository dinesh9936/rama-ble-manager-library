package com.rama.blecore.internal.connection

import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import kotlin.time.Duration.Companion.seconds

class GattCleanupControllerTest {

    @Test
    fun `cleanup after attachment closes GATT once`(): Unit = runBlocking {
        val controller = GattCleanupController()
        val gatt = mock(BluetoothGatt::class.java)

        controller.attach(gatt)

        controller.cleanup()
        controller.cleanup()

        controller.awaitCleanup()

        verify(gatt, times(1)).close()
    }

    @Test
    fun `cleanup before attachment waits until GATT is attached`() = runBlocking {
        val controller = GattCleanupController()
        val gatt = mock(BluetoothGatt::class.java)

        controller.cleanup()

        val completed = CompletableDeferred<Unit>()

        val waiter = async {
            controller.awaitCleanup()
            completed.complete(Unit)
        }

        assertFalse(completed.isCompleted)

        controller.attach(gatt)

        withTimeout(2.seconds) {
            waiter.await()
        }

        assertTrue(completed.isCompleted)
        verify(gatt, times(1)).close()
    }

    @Test
    fun `completeWithoutGatt completes cleanup`() = runBlocking {
        val controller = GattCleanupController()

        controller.completeWithoutGatt()

        withTimeout(2.seconds) {
            controller.awaitCleanup()
        }

        assertTrue(controller.isCleanupRequested())
    }

    @Test
    fun `multiple cleanup requests close GATT once`() = runBlocking {
        val controller = GattCleanupController()
        val gatt = mock(BluetoothGatt::class.java)

        controller.attach(gatt)

        val jobs = (1..20).map {
            async {
                controller.cleanup()
            }
        }

        jobs.forEach { it.await() }

        controller.awaitCleanup()

        verify(gatt, times(1)).close()
    }

    @Test
    fun `multiple waiters complete after cleanup`() = runBlocking {
        val controller = GattCleanupController()
        val gatt = mock(BluetoothGatt::class.java)

        controller.attach(gatt)

        val waiters = (1..10).map {
            async {
                controller.awaitCleanup()
            }
        }

        controller.cleanup()

        withTimeout(2.seconds) {
            waiters.forEach { it.await() }
        }

        verify(gatt, times(1)).close()
    }
}
package com.rama.blecore.internal.connection

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BleConnectionRegistryTest {

    private val addressA = "AA:BB:CC:DD:EE:01"
    private val addressB = "AA:BB:CC:DD:EE:02"

    @Test
    fun `concurrent reservations allow only one connection`() = runBlocking {

        val registry = BleConnectionRegistry()

        val address = "AA:BB:CC:DD:EE:01"

        val results = (1..100).map {
            async(Dispatchers.Default) {
                runCatching {
                    registry.reserve(address)
                }
            }
        }.awaitAll()

        val successful = results.filter { it.isSuccess }
        val failed = results.filter { it.isFailure }

        assertEquals(1, successful.size)
        assertEquals(99, failed.size)

        assertTrue(
            failed.all { result ->
                val exception = result.exceptionOrNull()

                exception is BleException &&
                        exception.error is BleError.AlreadyConnecting
            }
        )
    }

    @Test
    fun `duplicate while connecting returns AlreadyConnecting`() {
        val registry = BleConnectionRegistry()

        registry.reserve(addressA)

        try {
            registry.reserve(addressA)
            fail("Expected AlreadyConnecting")
        } catch (e: BleException) {
            assertTrue(e.error is BleError.AlreadyConnecting)
        }
    }

    @Test
    fun `duplicate while connected returns AlreadyConnected`() {
        val registry = BleConnectionRegistry()

        val token = registry.reserve(addressA)
        registry.markConnected(addressA, token)

        try {
            registry.reserve(addressA)
            fail("Expected AlreadyConnected")
        } catch (e: BleException) {
            assertTrue(e.error is BleError.AlreadyConnected)
        }
    }

    @Test
    fun `different devices can be reserved`() {
        val registry = BleConnectionRegistry()

        val tokenA = registry.reserve(addressA)
        val tokenB = registry.reserve(addressB)

        assertNotSame(tokenA, tokenB)
    }

    @Test
    fun `released device can reconnect`() {
        val registry = BleConnectionRegistry()

        val firstToken = registry.reserve(addressA)
        registry.release(addressA, firstToken)

        val secondToken = registry.reserve(addressA)

        assertNotSame(firstToken, secondToken)
    }

    @Test
    fun `old token cannot release newer reservation`() {
        val registry = BleConnectionRegistry()

        val oldToken = registry.reserve(addressA)
        registry.release(addressA, oldToken)

        val newToken = registry.reserve(addressA)

        registry.release(addressA, oldToken)

        try {
            registry.reserve(addressA)
            fail("New reservation should remain active")
        } catch (e: BleException) {
            assertTrue(e.error is BleError.AlreadyConnecting)
        }

        registry.release(addressA, newToken)
    }

    @Test
    fun `address comparison is case insensitive`() {
        val registry = BleConnectionRegistry()

        registry.reserve("AA:BB:CC:DD:EE:01")

        try {
            registry.reserve("aa:bb:cc:dd:ee:01")
            fail("Expected duplicate connection error")
        } catch (e: BleException) {
            assertTrue(e.error is BleError.AlreadyConnecting)
        }
    }
}
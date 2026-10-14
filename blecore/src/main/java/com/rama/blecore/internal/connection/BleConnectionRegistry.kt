package com.rama.blecore.internal.connection

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException
import java.util.Locale

internal class BleConnectionRegistry {

    private enum class Status {
        CONNECTING,
        CONNECTED
    }

    private data class Entry(
        val token: Any,
        val status: Status
    )

    private val lock = Any()

    private val connections =
        mutableMapOf<String, Entry>()

    fun reserve(address: String): Any =
        synchronized(lock) {

            val key = normalizeAddress(address)
            val existing = connections[key]

            if (existing != null) {
                val error = when (existing.status) {
                    Status.CONNECTING ->
                        BleError.AlreadyConnecting(address)

                    Status.CONNECTED ->
                        BleError.AlreadyConnected(address)
                }

                throw BleException(error)
            }

            val token = Any()

            connections[key] = Entry(
                token = token,
                status = Status.CONNECTING
            )

            token
        }

    fun markConnected(
        address: String,
        token: Any
    ) {
        synchronized(lock) {

            val key = normalizeAddress(address)
            val entry = connections[key]

            if (entry?.token === token) {
                connections[key] = entry.copy(
                    status = Status.CONNECTED
                )
            }
        }
    }

    fun release(
        address: String,
        token: Any
    ) {
        synchronized(lock) {

            val key = normalizeAddress(address)
            val entry = connections[key]

            if (entry?.token === token) {
                connections.remove(key)
            }
        }
    }

    private fun normalizeAddress(address: String): String {
        return address.uppercase(Locale.ROOT)
    }
}
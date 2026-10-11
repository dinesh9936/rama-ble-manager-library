package com.rama.blecore.internal.connection

import com.rama.blecore.error.BleError
import com.rama.blecore.error.BleException

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

            val existing = connections[address]

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

            connections[address] = Entry(
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

            val entry = connections[address]

            if (entry?.token === token) {
                connections[address] = entry.copy(
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

            val entry = connections[address]

            if (entry?.token === token) {
                connections.remove(address)
            }
        }
    }
}
package com.rama.blecore.internal.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.util.Log
import kotlinx.coroutines.CompletableDeferred

/**
 * Thread-safe controller for local BluetoothGatt cleanup.
 *
 * Guarantees:
 * - Cleanup can be requested before GATT attachment.
 * - GATT close is attempted at most once.
 * - Concurrent cleanup requests are safe.
 * - awaitCleanup() completes only after the close attempt.
 *
 * Does not guarantee that the remote peripheral has disconnected.
 */
internal class GattCleanupController {

    private companion object {
        const val TAG = "GattCleanupController"
    }

    private val lock = Any()

    private var bluetoothGatt: BluetoothGatt? = null

    private var cleanupRequested = false

    private var attachmentFinished = false

    private val cleanupDeferred = CompletableDeferred<Unit>()

    fun isCleanupRequested(): Boolean =
        synchronized(lock) {
            cleanupRequested
        }

    fun attach(gatt: BluetoothGatt) {
        val gattToClose = synchronized(lock) {
            check(!attachmentFinished) {
                "GATT attachment already finished"
            }

            attachmentFinished = true

            if (cleanupRequested) {
                gatt
            } else {
                bluetoothGatt = gatt
                null
            }
        }

        gattToClose?.let(::closeGatt)
    }

    fun completeWithoutGatt() {
        val shouldComplete = synchronized(lock) {
            if (attachmentFinished) {
                false
            } else {
                attachmentFinished = true
                cleanupRequested = true
                true
            }
        }

        if (shouldComplete) {
            cleanupDeferred.complete(Unit)
        }
    }

    fun cleanup() {
        val gattToClose: BluetoothGatt?
        val shouldCompleteWithoutGatt: Boolean

        synchronized(lock) {
            if (cleanupRequested) {
                return
            }

            cleanupRequested = true

            if (!attachmentFinished) {
                return
            }

            gattToClose = bluetoothGatt
            bluetoothGatt = null

            shouldCompleteWithoutGatt = gattToClose == null
        }

        if (shouldCompleteWithoutGatt) {
            cleanupDeferred.complete(Unit)
        } else {
            gattToClose?.let(::closeGatt)
        }
    }

    suspend fun awaitCleanup() {
        cleanupDeferred.await()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {
        try {
            gatt.close()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "BluetoothGatt.close() failed",
                exception
            )
        } finally {
            cleanupDeferred.complete(Unit)
        }
    }
}
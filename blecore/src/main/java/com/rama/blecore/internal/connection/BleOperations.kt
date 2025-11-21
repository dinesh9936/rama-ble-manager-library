package com.rama.blecore.internal.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.rama.blecore.internal.utils.BleLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BleOperations(
    private val gatt: BluetoothGatt,
    private val gattCallback: BleGattCallback,
    private val commandQueue: BleCommandQueue
) {

    @SuppressLint("MissingPermission")
    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        timeout: Duration = 5.seconds
    ): ByteArray? = commandQueue.enqueue {

        BleLogger.d("Reading from characteristic: ${characteristic.uuid}")

        withTimeout(timeout.inWholeMilliseconds) {
            suspendCancellableCoroutine { continuation ->

                val started = gatt.readCharacteristic(characteristic)
                if (!started) {
                    BleLogger.e("Read failed to start for ${characteristic.uuid}")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    BleLogger.w("Read operation cancelled")
                }

                // Use non-suspending tryReceive() here because we're inside the
                // suspendCancellableCoroutine callback (which is not a suspend
                // context). receiveCatching() is a suspending function and causes
                // a compile error.
                val result = gattCallback.readChannel.tryReceive().getOrNull()

                BleLogger.d("Read success: ${characteristic.uuid} → ${result?.size} bytes")
                continuation.resume(result)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        timeout: Duration = 5.seconds
    ): Boolean = commandQueue.enqueue {

        BleLogger.d("Writing to characteristic: ${characteristic.uuid} → ${data.size} bytes")

        withTimeout(timeout.inWholeMilliseconds) {
            suspendCancellableCoroutine { continuation ->

                // Use setValue to avoid deprecated 'value' property
                characteristic.value = data

                val started = gatt.writeCharacteristic(characteristic)
                if (!started) {
                    BleLogger.e("Write failed to start for ${characteristic.uuid}")
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                // Use non-suspending tryReceive() for the same reason as above.
                val success = gattCallback.writeChannel.tryReceive().getOrNull() ?: false

                BleLogger.d("Write success: $success")
                continuation.resume(success)
            }
        }
    }

    @Suppress("unused")
    fun readByUUID(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return gatt.getService(serviceUUID)
            ?.getCharacteristic(characteristicUUID)
    }

    @Suppress("unused")
    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return gatt.getService(serviceUUID)
            ?.getCharacteristic(characteristicUUID)
    }
}

package com.rama.blecore.internal.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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

    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        timeout: Duration = 5.seconds
    ): ByteArray? = commandQueue.enqueue {
        withTimeout(timeout){
            suspendCancellableCoroutine { continuation ->
                val success = gatt.readCharacteristic(characteristic)
                if (!success){
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation {

                }

                val result = gattCallback.readChannel.receiveCatching().getOrNull()
                continuation.resume(result)
            }
        }
    }

    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        timeout: Duration = 5.seconds
    ): Boolean = commandQueue.enqueue {
        withTimeout(timeout){
            suspendCancellableCoroutine { continuation ->
                characteristic.value = data
                val started = gatt.writeCharacteristic(characteristic)
                if (!started){
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                val success = gattCallback.writeChannel.receiveCatching()
                    .getOrNull() ?: false
                continuation.resume(success)
            }
        }
    }

    fun readByUUID(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
      return gatt.getService(serviceUUID)
          ?.getCharacteristic(characteristicUUID)
    }

    fun getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
        return gatt.getService(serviceUUID)
            ?.getCharacteristic(characteristicUUID)
    }
}
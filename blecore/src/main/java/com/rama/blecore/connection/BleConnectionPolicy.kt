package com.rama.blecore.connection

/**
 * Defines how a [com.rama.blecore.client.BleClient]
 * manages connections to BLE peripherals.
 *
 * This policy applies to connections managed by
 * a single BleClient instance.
 *
 * Duplicate connection requests to the same device
 * are rejected regardless of the selected policy.
 */
enum class BleConnectionPolicy {

    /**
     * Allows only one managed BLE device at a time.
     *
     * Connecting to a different device disconnects
     * the previous connection before starting the new one.
     *
     * The library must wait for the previous connection
     * to terminate and release its resources.
     */
    SINGLE_DEVICE,

    /**
     * Allows simultaneous connections to different
     * BLE peripherals.
     *
     * Each connection has an independent lifecycle.
     */
    MULTI_DEVICE
}
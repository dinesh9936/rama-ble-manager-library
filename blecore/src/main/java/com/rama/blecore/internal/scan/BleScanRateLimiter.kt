package com.rama.blecore.internal.scan


import android.os.SystemClock
import java.util.ArrayDeque

internal class BleScanRateLimiter(
    private val maxScans: Int = 4,
    private val windowMillis: Long = 30_000L
) {

    private val scanStartTimes = ArrayDeque<Long>()

    init {
        require(maxScans > 0)
        require(windowMillis > 0)
    }

    @Synchronized
    fun tryAcquire(): Long? {

        val now = SystemClock.elapsedRealtime()

        // Remove scans outside the rolling window.
        while (
            scanStartTimes.isNotEmpty() &&
            now - scanStartTimes.first >= windowMillis
        ) {
            scanStartTimes.removeFirst()
        }

        // Scan allowed.
        if (scanStartTimes.size < maxScans) {
            scanStartTimes.addLast(now)
            return null
        }

        // Return remaining cooldown in milliseconds.
        val oldestScan = scanStartTimes.first

        return (
                windowMillis - (now - oldestScan)
                ).coerceAtLeast(0L)
    }
}
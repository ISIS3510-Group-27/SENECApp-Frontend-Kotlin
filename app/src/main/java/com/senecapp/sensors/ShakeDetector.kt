package com.senecapp.sensors

import kotlin.math.sqrt

/** Two distinct acceleration peaks, rather than a tilt or one accidental bump. */
internal class ShakeDetector {
    private var firstPeak: Long? = null
    private var armed = true
    private var lastRefresh: Long? = null

    fun sample(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            resetMotion()
            return false
        }
        val g = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z) / 9.80665
        if (g < 1.3) armed = true
        if (g < 2.7 || !armed) return false
        armed = false
        if (lastRefresh?.let { timeMs - it < 10_000L } == true) return false

        val previous = firstPeak
        if (previous != null && timeMs - previous in 100L..1_000L) {
            firstPeak = null
            lastRefresh = timeMs
            return true
        }
        firstPeak = timeMs
        return false
    }

    // Lifecycle/busy resets discard partial gestures but preserve the cooldown.
    fun resetMotion() {
        firstPeak = null
        armed = true
    }
}

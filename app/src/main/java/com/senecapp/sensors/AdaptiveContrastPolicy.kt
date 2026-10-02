package com.senecapp.sensors

internal class AdaptiveContrastPolicy {
    var highContrast: Boolean = false
        private set

    fun update(lux: Float): Boolean {
        if (!lux.isFinite() || lux < 0f) return highContrast

        if (!highContrast && lux >= 500f) highContrast = true
        if (highContrast && lux <= 250f) highContrast = false
        return highContrast
    }

    fun reset() {
        highContrast = false
    }
}

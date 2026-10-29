package com.senecapp

import android.app.Application
import com.senecapp.analytics.AppTelemetry

class SENECApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase's existing provider initialization remains unchanged; telemetry never uses auth.
        try { AppTelemetry.get(this) } catch (_: Throwable) { /* Telemetry must not prevent startup. */ }
    }
}

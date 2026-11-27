package com.senecapp

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath
import com.senecapp.analytics.AppTelemetry

class SENECApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("organization_images").toOkioPath())
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        // Firebase's existing provider initialization remains unchanged; telemetry never uses auth.
        try { AppTelemetry.get(this) } catch (_: Throwable) { /* Telemetry must not prevent startup. */ }
    }
}

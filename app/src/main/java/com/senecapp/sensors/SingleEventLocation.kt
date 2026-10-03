package com.senecapp.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.senecapp.data.EventCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun hasEventLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// One fresh fix only, with a deadline and cleanup on completion/cancellation.
// No cached location, background permission, storage, or continuous tracking.
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal suspend fun readSingleEventLocation(context: Context): EventCoordinates? = withContext(Dispatchers.Main) {
    if (!hasEventLocationPermission(context)) return@withContext null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providers = if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        else listOf(LocationManager.NETWORK_PROVIDER)
    val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return@withContext null
    var listener: LocationListener? = null
    try {
        withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { continuation ->
                val singleListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            val coordinates = runCatching { EventCoordinates(location.latitude, location.longitude) }.getOrNull()
                            continuation.resumeWith(Result.success(coordinates))
                        }
                    }
                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(null))
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                listener = singleListener
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(singleListener) } }
                manager.requestSingleUpdate(provider, singleListener, Looper.getMainLooper())
            }
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } finally {
        listener?.let { runCatching { manager.removeUpdates(it) } }
    }
}

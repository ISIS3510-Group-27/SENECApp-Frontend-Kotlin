package com.senecapp.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.senecapp.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

// Lazily initialized by an assigned screen; never touches Firebase or the authenticated API client.
internal class AppTelemetry private constructor(application: Application) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val metadata = AnalyticsMetadata(BuildConfig.VERSION_NAME.take(32),
        "${Build.MANUFACTURER} ${Build.MODEL}".take(100), Build.VERSION.RELEASE.take(50))
    private val preferences by lazy { application.getSharedPreferences("bq1_analytics_outbox", Context.MODE_PRIVATE) }
    private val pipeline = AnalyticsPipeline(AnalyticsOutboxStore { preferences },
        AnonymousAnalyticsTransport(BuildConfig.API_BASE_URL.trimEnd('/') + "/analytics/events"), log = {
            if (BuildConfig.DEBUG) Log.d("BQ1Analytics", it)
        })
    private var sessionId = UUID.randomUUID().toString()
    private var backgroundAt: Long? = null
    @Volatile private var active = false
    private val tracker = ScreenVisitTracker(
        view = { screen -> record(AnalyticsEvent.view(screen, sessionId, metadata)) },
        error = { screen, failure -> record(AnalyticsEvent.error(screen, sessionId, metadata, failure)) })

    init {
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            while (isActive) { delay(30_000); if (active) pipeline.flush(force = true) }
        }
        // Registration is IO work; a callback only schedules a flush, it never performs network IO itself.
        scope.launch {
            try {
                val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    private var validated = false
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        val available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        if (available && !validated && active) scope.launch { pipeline.flush(force = true) }
                        validated = available
                    }
                    override fun onLost(network: Network) { validated = false }
                })
            } catch (_: Exception) { /* Periodic and foreground retries remain available. */ }
        }
    }

    fun attach(owner: String, key: String, screen: AnalyticsScreen, visible: Boolean) {
        if (visible) foreground()
        tracker.attach(owner, key, screen, visible)
    }
    fun content(owner: String, meaningful: Boolean, failure: TelemetryFailure?) = tracker.content(owner, meaningful, failure)
    fun visible(owner: String, visible: Boolean) {
        if (visible) foreground()
        tracker.visible(owner, visible)
    }
    fun detach(owner: String, rotating: Boolean) = tracker.detach(owner, rotating)

    private fun record(event: AnalyticsEvent) { scope.launch { pipeline.enqueue(event) } }
    private fun foreground() {
        if (!active) {
            val elapsed = backgroundAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
            if (elapsed >= 30 * 60_000L) sessionId = UUID.randomUUID().toString()
            active = true
            backgroundAt = null
            scope.launch { pipeline.flush(force = true) }
        }
    }

    override fun onActivityResumed(activity: Activity) { foreground() }
    override fun onActivityPaused(activity: Activity) {
        active = false
        backgroundAt = android.os.SystemClock.elapsedRealtime()
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        @Volatile private var instance: AppTelemetry? = null
        fun get(context: Context): AppTelemetry? = try {
            instance ?: synchronized(this) {
                instance ?: AppTelemetry(context.applicationContext as Application).also { instance = it }
            }
        } catch (_: Exception) { null }
    }
}

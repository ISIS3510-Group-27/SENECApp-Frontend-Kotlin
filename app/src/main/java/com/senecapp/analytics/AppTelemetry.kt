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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

// Initialized by Application; never touches Firebase or the authenticated API client.
internal class AppTelemetry private constructor(application: Application) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val metadata = AnalyticsMetadata(BuildConfig.VERSION_NAME.take(32),
        "${Build.MANUFACTURER} ${Build.MODEL}".take(100), Build.VERSION.RELEASE.take(50))
    private val preferences by lazy { application.getSharedPreferences("bq1_analytics_outbox", Context.MODE_PRIVATE) }
    private val outbox = AnalyticsOutboxStore { preferences }
    private val recoveryComplete = CompletableDeferred<Unit>()
    private val pipeline = AnalyticsPipeline(outbox,
        AnonymousAnalyticsTransport(BuildConfig.API_BASE_URL.trimEnd('/') + "/analytics/events"), log = {
            if (BuildConfig.DEBUG) Log.d("BQ1Analytics", it)
        })
    private var sessionId = UUID.randomUUID().toString()
    private var backgroundAt: Long? = null
    @Volatile private var active = false
    private val tracker = ScreenVisitTracker(
        view = { screen, loadTimeMs ->
            if (BuildConfig.DEBUG && loadTimeMs != null) Log.d("BQ11Analytics", "screen=${screen.wireName} load_time_ms=$loadTimeMs")
            record(AnalyticsEvent.view(screen, sessionId, metadata, loadTimeMs))
        },
        error = { screen, failure -> record(AnalyticsEvent.error(screen, sessionId, metadata, failure)) })

    init {
        val crashes = PendingCrashStore(application.getSharedPreferences("bq1_pending_crashes", Context.MODE_PRIVATE))
        TelemetryCrashHandler.install(crashes) { if (active) tracker.crashScreen else AnalyticsScreen.UNKNOWN }
        scope.launch {
            try {
                val recovered = crashes.recoverInto(outbox) { AnalyticsEvent.crash(it, sessionId, metadata) }
                if (BuildConfig.DEBUG && recovered > 0) Log.d("BQ1Analytics", "RECOVERED fatal records=$recovered")
            } finally {
                // Release all normal analytics even if storage fails; crash markers remain for next startup.
                recoveryComplete.complete(Unit)
            }
        }
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            while (isActive) { delay(30_000); if (active) flush() }
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
                        if (available && !validated && active) scope.launch { flush() }
                        validated = available
                    }
                    override fun onLost(network: Network) { validated = false }
                })
            } catch (_: Exception) { /* Periodic and foreground retries remain available. */ }
        }
    }

    fun attach(owner: String, key: String, screen: AnalyticsScreen, visible: Boolean, measureLoad: Boolean = false) {
        if (visible) foreground()
        tracker.attach(owner, key, screen, visible, measureLoad)
    }
    fun content(owner: String, meaningful: Boolean, failure: TelemetryFailure?, loadFailed: Boolean = false) =
        tracker.content(owner, meaningful, failure, loadFailed)
    fun visible(owner: String, visible: Boolean) {
        if (visible) foreground()
        tracker.visible(owner, visible)
    }
    fun detach(owner: String, rotating: Boolean) = tracker.detach(owner, rotating)

    private fun record(event: AnalyticsEvent) {
        scope.launch { recoveryComplete.await(); pipeline.enqueue(event) }
    }
    private suspend fun flush() { recoveryComplete.await(); pipeline.flush(force = true) }
    private fun foreground() {
        if (!active) {
            val elapsed = backgroundAt?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
            if (elapsed >= 30 * 60_000L) sessionId = UUID.randomUUID().toString()
            active = true
            backgroundAt = null
            scope.launch { flush() }
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

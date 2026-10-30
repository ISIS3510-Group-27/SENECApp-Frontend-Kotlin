package com.senecapp.analytics


// Main-thread screen attachment is separate from data loading and network delivery.
internal class ScreenVisitTracker(private val view: (AnalyticsScreen, Long?) -> Unit,
    private val error: (AnalyticsScreen, TelemetryFailure) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Visit(val key: String, val screen: AnalyticsScreen, var owner: String?,
        var visible: Boolean, val measureLoad: Boolean, var activeSince: Long?, var elapsedMs: Long = 0,
        var readyMs: Long? = null, var timingValid: Boolean = true,
        var meaningful: Boolean = false, var viewed: Boolean = false, var failure: TelemetryFailure? = null)
    private var visit: Visit? = null
    private val reportedErrors = linkedSetOf<String>()
    // The uncaught handler can run on any thread; it only reads this immutable snapshot.
    @Volatile var crashScreen: AnalyticsScreen = AnalyticsScreen.UNKNOWN
        private set

    fun attach(owner: String, key: String, screen: AnalyticsScreen, visible: Boolean, measureLoad: Boolean = false) {
        val old = visit
        visit = if (old != null && old.owner == null && old.key == key) old.apply {
            this.owner = owner; visibility(this, visible)
        } else Visit(key, screen, owner, visible, measureLoad, if (visible) clock() else null)
        emit()
    }

    fun content(owner: String, meaningful: Boolean, failure: TelemetryFailure?, loadFailed: Boolean = false) {
        visit?.takeIf { it.owner == owner }?.let {
            // Failed loads and time spent waiting to retry are not successful load samples.
            if (!it.viewed && (loadFailed || failure != null)) it.timingValid = false
            if (meaningful && it.readyMs == null) it.readyMs = elapsed(it)
            it.meaningful = meaningful; it.failure = failure
        }
        emit()
    }

    fun visible(owner: String, visible: Boolean) {
        visit?.takeIf { it.owner == owner }?.let { visibility(it, visible) }
        emit()
    }

    fun detach(owner: String, rotating: Boolean) {
        visit?.takeIf { it.owner == owner }?.let {
            if (rotating) { visibility(it, false); it.owner = null } else visit = null
        }
        updateCrashScreen()
    }

    private fun elapsed(current: Visit): Long = current.elapsedMs +
        (current.activeSince?.let { (clock() - it).coerceAtLeast(0) } ?: 0)

    private fun visibility(current: Visit, visible: Boolean) {
        current.elapsedMs = elapsed(current)
        current.activeSince = if (visible) clock() else null
        current.visible = visible
    }

    private fun updateCrashScreen() {
        crashScreen = visit?.takeIf { it.visible && it.owner != null }?.screen ?: AnalyticsScreen.UNKNOWN
    }

    private fun emit() {
        updateCrashScreen()
        val current = visit?.takeIf { it.visible && it.owner != null } ?: return
        if (current.meaningful && !current.viewed) {
            current.viewed = true
            view(current.screen, current.readyMs.takeIf { current.measureLoad && current.timingValid })
        }
        current.failure?.let {
            if (reportedErrors.add(it.id)) {
                if (reportedErrors.size > 500) reportedErrors.remove(reportedErrors.first())
                error(current.screen, it)
            }
        }
    }
}

package com.senecapp.analytics

// Main-thread screen attachment is separate from data loading and network delivery.
internal class ScreenVisitTracker(private val view: (AnalyticsScreen) -> Unit,
    private val error: (AnalyticsScreen, TelemetryFailure) -> Unit) {
    private data class Visit(val key: String, val screen: AnalyticsScreen, var owner: String?,
        var visible: Boolean, var meaningful: Boolean = false, var viewed: Boolean = false, var failure: TelemetryFailure? = null)
    private var visit: Visit? = null
    private val reportedErrors = linkedSetOf<String>()

    fun attach(owner: String, key: String, screen: AnalyticsScreen, visible: Boolean) {
        val old = visit
        visit = if (old != null && old.owner == null && old.key == key) old.apply { this.owner = owner; this.visible = visible }
        else Visit(key, screen, owner, visible)
        emit()
    }

    fun content(owner: String, meaningful: Boolean, failure: TelemetryFailure?) {
        visit?.takeIf { it.owner == owner }?.let { it.meaningful = meaningful; it.failure = failure }
        emit()
    }

    fun visible(owner: String, visible: Boolean) {
        visit?.takeIf { it.owner == owner }?.visible = visible
        emit()
    }

    fun detach(owner: String, rotating: Boolean) {
        visit?.takeIf { it.owner == owner }?.let {
            if (rotating) { it.owner = null; it.visible = false } else visit = null
        }
    }

    private fun emit() {
        val current = visit?.takeIf { it.visible && it.owner != null } ?: return
        if (current.meaningful && !current.viewed) { current.viewed = true; view(current.screen) }
        current.failure?.let {
            if (reportedErrors.add(it.id)) {
                if (reportedErrors.size > 500) reportedErrors.remove(reportedErrors.first())
                error(current.screen, it)
            }
        }
    }
}

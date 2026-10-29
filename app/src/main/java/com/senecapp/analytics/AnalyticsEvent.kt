package com.senecapp.analytics

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class AnalyticsScreen(val wireName: String, val feature: String) {
    DISCOVER("discover", "explore"), GROUP_DETAIL("group_detail", "group_profile"),
    EVENTS("events", "events"), EVENT_DETAIL("event_detail", "events"), PROFILE("profile", "profile"),
    REGISTRATION("registration", "auth"), EMAIL_VERIFICATION("email_verification", "auth"),
    UNKNOWN("unknown", "unknown")
}

// No Throwable, message, form fields or stack trace is retained in UI state or persisted.
class TelemetryFailure private constructor(val id: String, val occurredAt: String, val errorType: String) {
    companion object {
        fun from(failure: Exception): TelemetryFailure? {
            if (failure is CancellationException) return null
            val type = failure.javaClass.simpleName.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,99}")) } ?: "Exception"
            return TelemetryFailure(UUID.randomUUID().toString(), Instant.now().toString(), type)
        }
    }
}

internal data class AnalyticsMetadata(val appVersion: String, val deviceModel: String, val osVersion: String)

// The envelope has a closed set of fields; callers cannot attach arbitrary properties or PII.
@ConsistentCopyVisibility
internal data class AnalyticsEvent private constructor(val id: String, val occurredAt: String,
    val sessionId: String, val screen: AnalyticsScreen, val metadata: AnalyticsMetadata, val errorType: String? = null,
    val fatal: Boolean = false, val loadTimeMs: Long? = null) {
    init { require(loadTimeMs == null || (errorType == null && loadTimeMs >= 0)) }
    val name: String get() = if (errorType == null) "screen_view" else "app_error"

    fun json(): JSONObject = JSONObject().put("event_id", id).put("name", name).put("occurred_at", occurredAt)
        .put("session_id", sessionId).put("screen", screen.wireName).put("app", "kotlin").put("platform", "android")
        .put("app_version", metadata.appVersion).put("device_model", metadata.deviceModel).put("os_version", metadata.osVersion)
        .put("properties", JSONObject().apply {
            loadTimeMs?.let { put("load_time_ms", it) }
            if (errorType != null) { put("feature", screen.feature); put("error_type", errorType); put("fatal", fatal) }
        })

    companion object {
        fun view(screen: AnalyticsScreen, sessionId: String, metadata: AnalyticsMetadata,
            loadTimeMs: Long? = null,
            clock: () -> Instant = Instant::now): AnalyticsEvent =
            AnalyticsEvent(UUID.randomUUID().toString(), clock().toString(), sessionId, screen, metadata, loadTimeMs = loadTimeMs)

        fun error(screen: AnalyticsScreen, sessionId: String, metadata: AnalyticsMetadata, failure: TelemetryFailure): AnalyticsEvent =
            AnalyticsEvent(failure.id, failure.occurredAt, sessionId, screen, metadata, failure.errorType)

        fun crash(crash: PendingCrash, sessionId: String, metadata: AnalyticsMetadata): AnalyticsEvent =
            AnalyticsEvent(crash.id, crash.occurredAt, sessionId, crash.screen, metadata, crash.errorType, fatal = true)

        fun read(root: JSONObject): AnalyticsEvent {
            require(root.keys().asSequence().toSet() == setOf("event_id", "name", "occurred_at", "session_id", "screen",
                "app", "platform", "app_version", "device_model", "os_version", "properties"))
            require(root.getString("app") == "kotlin" && root.getString("platform") == "android")
            val id = root.getString("event_id").also { require(UUID.fromString(it).toString() == it) }
            val occurredAt = root.getString("occurred_at").also { Instant.parse(it) }
            val session = root.getString("session_id").also { require(UUID.fromString(it).toString() == it) }
            val screen = AnalyticsScreen.entries.single { it.wireName == root.getString("screen") }
            val metadata = AnalyticsMetadata(root.getString("app_version"), root.getString("device_model"), root.getString("os_version"))
            require(metadata.appVersion.length <= 32 && metadata.deviceModel.length <= 100 && metadata.osVersion.length <= 50)
            val properties = root.getJSONObject("properties")
            var loadTimeMs: Long? = null
            val error = when (root.getString("name")) {
                "screen_view" -> {
                    require(properties.length() == 0 || properties.keys().asSequence().toSet() == setOf("load_time_ms"))
                    if (properties.has("load_time_ms")) {
                        val value = properties.get("load_time_ms")
                        require(value is Int || value is Long)
                        loadTimeMs = (value as Number).toLong().also { require(it >= 0) }
                    }
                    null
                }
                "app_error" -> {
                    require(properties.keys().asSequence().toSet() == setOf("feature", "error_type", "fatal"))
                    require(properties.get("fatal") is Boolean && properties.getString("feature") == screen.feature)
                    properties.getString("error_type").also { require(it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,99}"))) }
                }
                else -> error("Unsupported analytics event")
            }
            val fatal = error != null && properties.getBoolean("fatal")
            require(screen != AnalyticsScreen.UNKNOWN || fatal)
            return AnalyticsEvent(id, occurredAt, session, screen, metadata, error, fatal, loadTimeMs)
        }
    }
}

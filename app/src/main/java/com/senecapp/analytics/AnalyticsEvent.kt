package com.senecapp.analytics

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class AnalyticsScreen(val wireName: String, val feature: String) {
    EVENTS("events", "events"), EVENT_DETAIL("event_detail", "events"), PROFILE("profile", "profile"),
    REGISTRATION("registration", "auth"), EMAIL_VERIFICATION("email_verification", "auth")
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
    val sessionId: String, val screen: AnalyticsScreen, val metadata: AnalyticsMetadata, val errorType: String? = null) {
    val name: String get() = if (errorType == null) "screen_view" else "app_error"

    fun json(): JSONObject = JSONObject().put("event_id", id).put("name", name).put("occurred_at", occurredAt)
        .put("session_id", sessionId).put("screen", screen.wireName).put("app", "kotlin").put("platform", "android")
        .put("app_version", metadata.appVersion).put("device_model", metadata.deviceModel).put("os_version", metadata.osVersion)
        .put("properties", JSONObject().apply {
            if (errorType != null) { put("feature", screen.feature); put("error_type", errorType); put("fatal", false) }
        })

    companion object {
        fun view(screen: AnalyticsScreen, sessionId: String, metadata: AnalyticsMetadata,
            clock: () -> Instant = Instant::now): AnalyticsEvent =
            AnalyticsEvent(UUID.randomUUID().toString(), clock().toString(), sessionId, screen, metadata)

        fun error(screen: AnalyticsScreen, sessionId: String, metadata: AnalyticsMetadata, failure: TelemetryFailure): AnalyticsEvent =
            AnalyticsEvent(failure.id, failure.occurredAt, sessionId, screen, metadata, failure.errorType)

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
            val error = when (root.getString("name")) {
                "screen_view" -> { require(properties.length() == 0); null }
                "app_error" -> {
                    require(properties.keys().asSequence().toSet() == setOf("feature", "error_type", "fatal"))
                    require(properties.get("fatal") == false && properties.getString("feature") == screen.feature)
                    properties.getString("error_type").also { require(it.matches(Regex("[A-Za-z][A-Za-z0-9_$]{0,99}"))) }
                }
                else -> error("Unsupported analytics event")
            }
            return AnalyticsEvent(id, occurredAt, session, screen, metadata, error)
        }
    }
}

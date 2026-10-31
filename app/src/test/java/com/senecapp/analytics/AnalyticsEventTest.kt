package com.senecapp.analytics

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

internal val testMetadata = AnalyticsMetadata("1.0", "Test model", "14")
internal val testSession = "12345678-1234-1234-1234-123456789abc"
internal fun testEvent(screen: AnalyticsScreen = AnalyticsScreen.EVENTS) =
    AnalyticsEvent.view(screen, testSession, testMetadata) { Instant.parse("2026-10-28T12:00:00Z") }

class AnalyticsEventTest {
    @Test fun measuredViewsRoundTripWithTheBackendLoadTimeAndDeviceFields() {
        val event = AnalyticsEvent.view(AnalyticsScreen.DISCOVER, testSession, testMetadata, loadTimeMs = 842)
        val json = event.json()
        assertEquals("discover", json.getString("screen"))
        assertEquals("Test model", json.getString("device_model"))
        assertEquals("14", json.getString("os_version"))
        assertEquals(setOf("load_time_ms"), json.getJSONObject("properties").keys().asSequence().toSet())
        assertEquals(842L, json.getJSONObject("properties").getLong("load_time_ms"))
        assertEquals(event, AnalyticsEvent.read(JSONObject(json.toString())))
    }

    @Test fun zeroDurationIsValidButNegativeDurationsAreRejected() {
        val zero = AnalyticsEvent.view(AnalyticsScreen.GROUP_DETAIL, testSession, testMetadata, loadTimeMs = 0)
        assertEquals(zero, AnalyticsEvent.read(JSONObject(zero.json().toString())))
        try {
            AnalyticsEvent.view(AnalyticsScreen.DISCOVER, testSession, testMetadata, loadTimeMs = -1)
            fail("Negative duration must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun invalidOrSensitiveLoadPropertiesCannotEnterTheOutbox() {
        for (value in listOf(-1, 1.5, "842", true, JSONObject.NULL)) {
            val json = testEvent().json().apply { getJSONObject("properties").put("load_time_ms", value) }
            try { AnalyticsEvent.read(json); fail("Invalid duration must be rejected") } catch (_: Exception) { }
        }
        val json = testEvent().json().apply {
            getJSONObject("properties").put("load_time_ms", 842).put("email", "private")
        }
        try { AnalyticsEvent.read(json); fail("Unexpected property must be rejected") } catch (_: Exception) { }
    }

    @Test fun screenViewHasTheExactEnvelopeWithoutInventedLoadTime() {
        val event = testEvent()
        val json = event.json()
        assertEquals("screen_view", json.getString("name"))
        assertEquals("kotlin", json.getString("app"))
        assertEquals("android", json.getString("platform"))
        assertEquals("events", json.getString("screen"))
        assertEquals("2026-10-28T12:00:00Z", json.getString("occurred_at"))
        assertEquals(0, json.getJSONObject("properties").length())
        UUID.fromString(event.id)
        assertNotEquals(event.id, testEvent().id)
        assertEquals(event, AnalyticsEvent.read(JSONObject(json.toString())))
    }

    @Test fun errorKeepsOriginalIdentityAndTimeButNeverExceptionContents() {
        val failure = TelemetryFailure.from(IOException("email=secret@uniandes.edu.co password=private token=hidden"))!!
        val event = AnalyticsEvent.error(AnalyticsScreen.EMAIL_VERIFICATION, testSession, testMetadata, failure)
        val json = event.json()
        val properties = json.getJSONObject("properties")
        assertEquals("app_error", event.name)
        assertEquals(failure.id, event.id)
        assertEquals(failure.occurredAt, event.occurredAt)
        assertEquals("auth", properties.getString("feature"))
        assertEquals("IOException", properties.getString("error_type"))
        assertEquals(false, properties.get("fatal"))
        assertEquals(3, properties.length())
        listOf("secret", "password", "token", "stack", "message").forEach { assertFalse(json.toString().contains(it)) }
        assertEquals(event, AnalyticsEvent.read(JSONObject(json.toString())))
    }

    @Test fun cancellationsAreNotFailures() {
        assertNull(TelemetryFailure.from(CancellationException("Navigation cancelled")))
    }

    @Test fun allAssignedScreenNamesAreSupported() {
        assertEquals(setOf("events", "event_detail", "profile", "registration", "email_verification", "discover", "group_detail",
            "my_rsos", "create_group", "check_in_scanner", "join_form"),
            AnalyticsScreen.entries.filter { it != AnalyticsScreen.UNKNOWN }
                .map { AnalyticsEvent.read(testEvent(it).json()).screen.wireName }.toSet())
    }

    @Test fun joinFormOpenedRoundTripsWithTheBackendProperties() {
        val attempt = UUID.randomUUID().toString()
        val event = AnalyticsEvent.joinFormOpened(7, attempt, testSession, testMetadata) { Instant.parse("2026-10-28T12:00:00Z") }
        val json = event.json()
        assertEquals("join_form_opened", json.getString("name"))
        assertEquals("join_form", json.getString("screen"))
        assertEquals(7, json.getJSONObject("properties").getInt("group_id"))
        assertEquals(attempt, json.getJSONObject("properties").getString("join_attempt_id"))
        assertEquals(event, AnalyticsEvent.read(JSONObject(json.toString())))
    }

    @Test fun fatalErrorsRoundTripWithOriginalIdentityAndAnUnknownScreen() {
        val crash = PendingCrash(UUID.randomUUID().toString(), "2026-10-28T12:00:00Z", AnalyticsScreen.UNKNOWN, "OutOfMemoryError")
        val event = AnalyticsEvent.crash(crash, testSession, testMetadata)
        assertEquals("app_error", event.name)
        assertEquals(crash.id, event.id)
        assertEquals(crash.occurredAt, event.occurredAt)
        assertEquals("unknown", event.json().getString("screen"))
        assertEquals(true, event.json().getJSONObject("properties").get("fatal"))
        assertEquals("unknown", event.json().getJSONObject("properties").getString("feature"))
        assertEquals(event, AnalyticsEvent.read(JSONObject(event.json().toString())))
    }

    @Test fun unexpectedOrSensitivePersistedPropertiesAreRejected() {
        val error = AnalyticsEvent.error(AnalyticsScreen.PROFILE, testSession, testMetadata, TelemetryFailure.from(IOException())!!)
        val invalid = listOf(testEvent().json().put("email", "private"),
            testEvent().json().put("screen", "unsupported_screen"),
            testEvent().json().put("name", "unexpected"),
            testEvent().json().put("event_id", "not-a-uuid"),
            testEvent().json().put("occurred_at", "not-a-date"),
            error.json().apply { getJSONObject("properties").put("message", "private") },
            error.json().apply { getJSONObject("properties").put("fatal", "false") },
            AnalyticsEvent.joinFormOpened(7, UUID.randomUUID().toString(), testSession, testMetadata).json()
                .apply { getJSONObject("properties").put("motivation", "private") })
        invalid.forEach { root ->
            try { AnalyticsEvent.read(root); fail("Expected invalid event rejection") } catch (_: Exception) { }
        }
    }
}

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
        assertEquals(setOf("events", "event_detail", "profile", "registration", "email_verification"),
            AnalyticsScreen.entries.map { AnalyticsEvent.read(testEvent(it).json()).screen.wireName }.toSet())
    }

    @Test fun unexpectedOrSensitivePersistedPropertiesAreRejected() {
        val error = AnalyticsEvent.error(AnalyticsScreen.PROFILE, testSession, testMetadata, TelemetryFailure.from(IOException())!!)
        val invalid = listOf(testEvent().json().put("email", "private"),
            testEvent().json().put("screen", "discover"),
            testEvent().json().put("name", "unexpected"),
            testEvent().json().put("event_id", "not-a-uuid"),
            testEvent().json().put("occurred_at", "not-a-date"),
            error.json().apply { getJSONObject("properties").put("message", "private") },
            error.json().apply { getJSONObject("properties").put("fatal", "false") })
        invalid.forEach { root ->
            try { AnalyticsEvent.read(root); fail("Expected invalid event rejection") } catch (_: Exception) { }
        }
    }
}

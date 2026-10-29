package com.senecapp.analytics

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AnonymousAnalyticsTransportTest {
    @Test fun measuredViewsReachTheSameBatchEndpointWithoutCredentialsOrExtraProperties() = runTest {
        val connection = Connection(202, """{"accepted":1,"duplicates":0}""")
        val sample = AnalyticsEvent.view(AnalyticsScreen.DISCOVER, testSession, testMetadata, loadTimeMs = 842)
        assertEquals(AnalyticsDelivery.Acknowledged(1, 0),
            AnonymousAnalyticsTransport("https://example.test/api/v1/analytics/events") { connection }.send(listOf(sample)))
        val posted = JSONObject(connection.body.toString("UTF-8")).getJSONArray("events").getJSONObject(0)
        assertEquals(sample, AnalyticsEvent.read(posted))
        assertEquals(842L, posted.getJSONObject("properties").getLong("load_time_ms"))
        assertEquals("Test model", posted.getString("device_model"))
        assertEquals("14", posted.getString("os_version"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.getRequestProperty("Cookie"))
    }

    private class Connection(private val status: Int, private val response: String) :
        HttpURLConnection(URL("https://example.test/api/v1/analytics/events")) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = body
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray())
    }

    @Test fun sendsAnonymousJsonWithTechnicalHeadersToTheExistingEndpoint() = runTest {
        val connection = Connection(202, """{"accepted":1,"duplicates":0}""")
        val transport = AnonymousAnalyticsTransport("https://example.test/api/v1/analytics/events") { url ->
            assertEquals("/api/v1/analytics/events", url.path)
            connection
        }
        val event = testEvent()
        assertEquals(AnalyticsDelivery.Acknowledged(1, 0), transport.send(listOf(event)))
        assertEquals("POST", connection.requestMethod)
        assertEquals("kotlin", connection.getRequestProperty("X-App"))
        assertEquals("android", connection.getRequestProperty("X-Platform"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.getRequestProperty("Cookie"))
        assertFalse(connection.instanceFollowRedirects)
        val posted = JSONObject(connection.body.toString("UTF-8")).getJSONArray("events")
        assertEquals(event, AnalyticsEvent.read(posted.getJSONObject(0)))
        assertTrue(connection.disconnected)
    }

    @Test fun duplicateAcknowledgmentIsSuccess() = runTest {
        val connection = Connection(202, """{"accepted":0,"duplicates":1}""")
        assertEquals(AnalyticsDelivery.Acknowledged(0, 1),
            AnonymousAnalyticsTransport("https://example.test") { connection }.send(listOf(testEvent())))
    }

    @Test fun recoveredFatalCrashIsPostedAnonymouslyWithItsOriginalUuidAndTimestamp() = runTest {
        val crash = PendingCrash(UUID.randomUUID().toString(), "2026-10-29T12:00:00Z", AnalyticsScreen.UNKNOWN, "IllegalStateException")
        val connection = Connection(202, """{"accepted":1,"duplicates":0}""")
        val event = AnalyticsEvent.crash(crash, testSession, testMetadata)
        assertEquals(AnalyticsDelivery.Acknowledged(1, 0),
            AnonymousAnalyticsTransport("https://example.test/api/v1/analytics/events") { connection }.send(listOf(event)))
        val posted = JSONObject(connection.body.toString("UTF-8")).getJSONArray("events").getJSONObject(0)
        assertEquals(crash.id, posted.getString("event_id"))
        assertEquals(crash.occurredAt, posted.getString("occurred_at"))
        assertEquals(true, posted.getJSONObject("properties").get("fatal"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.getRequestProperty("Cookie"))
        assertEquals(event, AnalyticsEvent.read(posted))
    }

    @Test fun transientHttpStatusesRemainRetryable() = runTest {
        for (status in listOf(408, 425, 429, 500, 503)) {
            val connection = Connection(status, "private response body")
            assertEquals(AnalyticsDelivery.Retry,
                AnonymousAnalyticsTransport("https://example.test") { connection }.send(listOf(testEvent())))
            assertTrue(connection.disconnected)
        }
    }

    @Test fun permanentFailuresAndRedirectsDoNotRetryForever() = runTest {
        for (status in listOf(301, 400, 401, 403, 404, 413, 422)) {
            val connection = Connection(status, "private response body")
            assertEquals(AnalyticsDelivery.Permanent(status),
                AnonymousAnalyticsTransport("https://example.test") { connection }.send(listOf(testEvent())))
        }
    }

    @Test fun incompleteMalformedOrNegativeAcknowledgmentsAreNotAccepted() = runTest {
        for (body in listOf("not json", "{}", """{"accepted":0,"duplicates":0}""", """{"accepted":-1,"duplicates":2}""")) {
            val connection = Connection(202, body)
            assertEquals(AnalyticsDelivery.Retry,
                AnonymousAnalyticsTransport("https://example.test") { connection }.send(listOf(testEvent())))
        }
    }
}

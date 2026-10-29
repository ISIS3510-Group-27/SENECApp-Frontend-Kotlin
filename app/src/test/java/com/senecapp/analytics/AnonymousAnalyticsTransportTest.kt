package com.senecapp.analytics

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AnonymousAnalyticsTransportTest {
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

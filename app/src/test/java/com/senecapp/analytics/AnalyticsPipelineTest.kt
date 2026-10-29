package com.senecapp.analytics

import com.senecapp.data.EventsTestPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsPipelineTest {
    private class Transport : AnalyticsTransport {
        val batches = mutableListOf<List<AnalyticsEvent>>()
        var response: suspend (List<AnalyticsEvent>) -> AnalyticsDelivery = { AnalyticsDelivery.Retry }
        override suspend fun send(events: List<AnalyticsEvent>): AnalyticsDelivery { batches += events; return response(events) }
    }

    @Test fun storesBeforeSendingAndAcknowledgmentRemovesOnlyDeliveredEvents() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val event = testEvent()
        val transport = Transport().apply { response = {
            assertEquals(listOf(event), AnalyticsOutboxStore { prefs.value }.read().map { it.event })
            AnalyticsDelivery.Acknowledged(1, 0)
        } }
        AnalyticsPipeline(store, transport).enqueue(event)
        assertTrue(store.read().isEmpty())
        assertEquals(listOf(event), transport.batches.single())
    }

    @Test fun failedPersistenceNeverSendsAnUnstoredEvent() = runTest {
        val prefs = EventsTestPreferences().apply { commitSucceeds = false }
        val transport = Transport()
        AnalyticsPipeline(AnalyticsOutboxStore { prefs.value }, transport).enqueue(testEvent())
        assertTrue(transport.batches.isEmpty())
    }

    @Test fun restartAndReconnectPreserveOriginalUuidAndTimestamp() = runTest {
        val prefs = EventsTestPreferences()
        val first = Transport().apply { response = { throw IOException("Offline with sensitive response") } }
        val original = testEvent()
        AnalyticsPipeline(AnalyticsOutboxStore { prefs.value }, first).enqueue(original)
        val second = Transport().apply { response = { AnalyticsDelivery.Acknowledged(0, it.size) } }
        val restoredStore = AnalyticsOutboxStore { prefs.value }
        AnalyticsPipeline(restoredStore, second).flush(force = true)
        assertEquals(original, second.batches.single().single())
        assertTrue(restoredStore.read().isEmpty())
    }

    @Test fun retriesAreThrottledForThirtySecondsAndReconnectCanWakeDelivery() = runTest {
        var now = 0L
        val prefs = EventsTestPreferences()
        val transport = Transport()
        val pipeline = AnalyticsPipeline(AnalyticsOutboxStore { prefs.value }, transport, clock = { now })
        pipeline.enqueue(testEvent())
        pipeline.flush()
        assertEquals(1, transport.batches.size)
        now = 29_999L; pipeline.flush()
        assertEquals(1, transport.batches.size)
        now = 30_000L; pipeline.flush()
        assertEquals(2, transport.batches.size)
        transport.response = { AnalyticsDelivery.Acknowledged(it.size, 0) }
        pipeline.flush(force = true)
        assertEquals(3, transport.batches.size)
        assertTrue(AnalyticsOutboxStore { prefs.value }.read().isEmpty())
    }

    @Test fun batchesNeverExceedFifty() = runTest {
        val prefs = EventsTestPreferences()
        val persistent = AnalyticsOutboxStore { prefs.value }
        val events = List(121) { testEvent() }
        persistent.write(events.map { QueuedAnalyticsEvent(it) })
        val transport = Transport().apply { response = { AnalyticsDelivery.Acknowledged(it.size, 0) } }
        AnalyticsPipeline(persistent, transport).flush()
        assertEquals(listOf(50, 50, 21), transport.batches.map { it.size })
        assertEquals(events, transport.batches.flatten())
        assertTrue(persistent.read().isEmpty())
    }

    @Test fun insertionDuringFlushCannotBeLostOrStartAConcurrentFlush() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val gate = CompletableDeferred<Unit>()
        val first = testEvent()
        val second = testEvent()
        val transport = Transport().apply { response = {
            if (it.any { event -> event.id == first.id }) gate.await()
            AnalyticsDelivery.Acknowledged(it.size, 0)
        } }
        val pipeline = AnalyticsPipeline(store, transport)
        val flushing = launch { pipeline.enqueue(first) }
        runCurrent()
        pipeline.enqueue(second)
        pipeline.flush(force = true)
        assertEquals(1, transport.batches.size)
        assertEquals(setOf(first.id, second.id), store.read().map { it.event.id }.toSet())
        gate.complete(Unit); flushing.join()
        assertEquals(listOf(first, second), transport.batches.flatten())
        assertTrue(store.read().isEmpty())
    }

    @Test fun duplicatePendingEventsAreNotInsertedTwice() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val event = testEvent()
        val transport = Transport()
        val pipeline = AnalyticsPipeline(store, transport)
        pipeline.enqueue(event); pipeline.enqueue(event)
        assertEquals(listOf(event), store.read().map { it.event })
        assertEquals(1, transport.batches.size)
    }

    @Test fun invalidBatchIsSplitAndOnlyRejectedEventIsQuarantined() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val events = List(3) { testEvent() }
        store.write(events.map { QueuedAnalyticsEvent(it) })
        val badId = events[1].id
        val transport = Transport().apply { response = {
            if (it.any { event -> event.id == badId }) AnalyticsDelivery.Permanent(422)
            else AnalyticsDelivery.Acknowledged(it.size, 0)
        } }
        val pipeline = AnalyticsPipeline(store, transport)
        pipeline.flush()
        assertEquals(listOf(QueuedAnalyticsEvent(events[1], quarantined = true)), store.read())
        val attempts = transport.batches.size
        pipeline.flush(force = true)
        assertEquals(attempts, transport.batches.size)
    }

    @Test fun permanentHttpFailuresAreRetainedButNotRetriedForever() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val transport = Transport().apply { response = { AnalyticsDelivery.Permanent(403) } }
        val pipeline = AnalyticsPipeline(store, transport)
        pipeline.enqueue(testEvent())
        repeat(3) { pipeline.flush(force = true) }
        assertEquals(1, transport.batches.size)
        assertTrue(store.read().single().quarantined)
    }

    @Test fun invalidAcknowledgmentCannotRemoveStoredEvents() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val transport = Transport().apply { response = { AnalyticsDelivery.Acknowledged(0, 0) } }
        AnalyticsPipeline(store, transport).enqueue(testEvent())
        assertEquals(1, store.read().size)
        assertFalse(store.read().single().quarantined)
    }

    @Test fun failedLocalAcknowledgmentIsSafelyRetriedWithTheSameEventId() = runTest {
        val prefs = EventsTestPreferences()
        val store = AnalyticsOutboxStore { prefs.value }
        val original = testEvent()
        val transport = Transport().apply { response = {
            prefs.commitSucceeds = false
            AnalyticsDelivery.Acknowledged(1, 0)
        } }
        val pipeline = AnalyticsPipeline(store, transport)
        pipeline.enqueue(original)
        assertEquals(original, store.read().single().event)
        prefs.commitSucceeds = true
        transport.response = { AnalyticsDelivery.Acknowledged(0, 1) }
        pipeline.flush(force = true)
        assertEquals(listOf(original, original), transport.batches.flatten())
        assertTrue(store.read().isEmpty())
    }

    @Test fun corruptedRowsAreNotDeliveredAndQueueIsBounded() = runTest {
        val prefs = EventsTestPreferences()
        val event = testEvent()
        val array = JSONArray().put(JSONObject().put("event", event.json()).put("quarantined", false))
            .put(JSONObject().put("event", event.json().put("password", "private")).put("quarantined", false))
            .put("broken")
        prefs.strings["outbox"] = array.toString()
        val store = AnalyticsOutboxStore { prefs.value }
        assertEquals(listOf(event), store.read().map { it.event })
        val events = List(510) { QueuedAnalyticsEvent(testEvent()) }
        store.write(events)
        assertEquals(events.takeLast(500), store.read())
        prefs.strings["outbox"] = "broken"
        assertTrue(store.read().isEmpty())
    }
}

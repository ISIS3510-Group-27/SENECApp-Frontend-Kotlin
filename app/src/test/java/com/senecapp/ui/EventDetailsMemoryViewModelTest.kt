package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.data.ApiHttpException
import com.senecapp.data.CampusEvent
import com.senecapp.data.CampusEventsResult
import com.senecapp.data.CampusEventsSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailsMemoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val event = CampusEvent(8, "Meetup", "Club", "2026-11-01T14:00:00Z", null, null, false)
    private var uid: String? = "a"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private data class Request(val id: Int, val source: String, val requestId: String?)
    private class Source(var response: suspend (Int) -> CampusEvent) : CampusEventsSource {
        val requests = mutableListOf<Request>()
        override suspend fun load(mine: Boolean) = CampusEventsResult(emptyList(), 0)
        override suspend fun detail(id: Int, source: String, requestId: String?): CampusEvent {
            requests += Request(id, source, requestId)
            return response(id)
        }
    }

    private fun model(source: Source, cache: FakeEventDetailsCache = FakeEventDetailsCache(), key: String = "events") =
        CampusEventsViewModel(Application(), source, cache) { uid }.also { store.put(key, it) }

    @Test fun missFetchesAndCachesThenHitSkipsBackend() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        assertEquals(event, cache.items[event.id])
        assertEquals(1, source.requests.size)
        vm.close()
        vm.openId(event.id)
        assertEquals(event, vm.state.selected)
        assertFalse(vm.state.detailLoading)
        assertNull(vm.state.detailError)
        advanceUntilIdle()
        assertEquals(1, source.requests.size)
    }

    @Test fun otherEntryPointsAlwaysCallBackendAndPreserveAnalyticsParameters() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        for (entryPoint in listOf("group_detail", "free_now", "recommendation", "notification")) {
            repeat(2) {
                vm.openId(event.id, entryPoint, requestId = "request")
                advanceUntilIdle()
                assertEquals(Request(event.id, entryPoint, "request"), source.requests.last())
            }
        }
        assertEquals(9, source.requests.size)
        assertEquals(1, cache.lookups)
        assertEquals(1, cache.writes)
    }

    @Test fun failedBackendRequestDoesNotReplaceAnExistingValidEntry() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        source.response = { throw IOException("Offline") }
        vm.openId(event.id, "group_detail"); advanceUntilIdle()
        assertNotNull(vm.state.detailError)
        assertEquals(event, cache.items[event.id])
        assertEquals(1, cache.writes)
        vm.open(event); advanceUntilIdle()
        assertEquals(2, source.requests.size)
        assertEquals(event, vm.state.selected)
        assertNull(vm.state.detailError)
    }

    @Test fun failedMissIsNotCachedAndRetryFetchesAgain() = runTest(dispatcher) {
        val source = Source { throw IOException("Offline") }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        assertTrue(cache.items.isEmpty())
        assertNotNull(vm.state.detailError)
        source.response = { event }
        vm.open(event); advanceUntilIdle()
        assertEquals(2, source.requests.size)
        assertEquals(event, cache.items[event.id])
        assertNull(vm.state.detailError)
    }

    @Test fun removedEntryRequiresAnotherBackendRequest() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        cache.invalidate(event.id)
        vm.open(event); advanceUntilIdle()
        assertEquals(2, source.requests.size)
    }

    @Test fun accountChangeOrSignOutCannotUsePreviousEntries() = runTest(dispatcher) {
        for (nextUid in listOf("b", null)) {
            uid = "a"
            val source = Source { event }
            val cache = FakeEventDetailsCache()
            val vm = model(source, cache)
            vm.open(event); advanceUntilIdle()
            val lookups = cache.lookups
            uid = nextUid
            vm.open(event); advanceUntilIdle()
            assertTrue(cache.items.isEmpty())
            assertEquals(lookups, cache.lookups)
            assertEquals(1, source.requests.size)
            assertNull(vm.state.selected)
        }
    }

    @Test fun lateResponseAfterAccountChangeIsNotCached() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEvent>()
        val source = Source { response.await() }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); runCurrent()
        uid = "b"
        response.complete(event); advanceUntilIdle()
        assertTrue(cache.items.isEmpty())
        assertNull(vm.state.selected)
    }

    @Test fun differentSessionsHaveIndependentCaches() = runTest(dispatcher) {
        val source = Source { event }
        val first = model(source, key = "a")
        first.open(event); advanceUntilIdle()
        uid = "b"
        val second = model(source, key = "b")
        second.open(event); advanceUntilIdle()
        assertEquals(2, source.requests.size)
    }

    @Test fun authorizationFailureClearsTheMemoryCache() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        source.response = { throw ApiHttpException(403) }
        vm.openId(event.id, "group_detail"); advanceUntilIdle()
        assertTrue(cache.items.isEmpty())
        assertNull(vm.state.selected)
    }

    @Test fun successfulCheckInInvalidatesCachedAttendance() = runTest(dispatcher) {
        val source = Source { event }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); advanceUntilIdle()
        vm.checkedIn(event.id)
        assertTrue(vm.state.selected!!.checkedIn)
        assertFalse(cache.items.containsKey(event.id))
        val updated = event.copy(checkedIn = true, attendeeCount = 7)
        source.response = { updated }
        vm.open(event); advanceUntilIdle()
        assertEquals(2, source.requests.size)
        assertEquals(updated, cache.items[event.id])
        assertEquals(updated, vm.state.selected)
    }

    @Test fun responseStartedBeforeCheckInIsRefetchedBeforeItCanBeCached() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEvent>()
        val updated = event.copy(checkedIn = true, attendeeCount = 7)
        val cache = FakeEventDetailsCache()
        var requests = 0
        val source = Source {
            if (++requests == 1) response.await() else {
                assertTrue(cache.items.isEmpty())
                updated
            }
        }
        val vm = model(source, cache)
        vm.open(event); runCurrent()
        vm.checkedIn(event.id)
        response.complete(event); advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(1, cache.writes)
        assertEquals(updated, cache.items[event.id])
        assertEquals(updated, vm.state.selected)
    }

    @Test fun cancelledOldRequestCannotOverwriteOrPopulateCache() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEvent>()
        val source = Source { id ->
            if (id == event.id) withContext(NonCancellable) { response.await() } else event.copy(id = id)
        }
        val cache = FakeEventDetailsCache()
        val vm = model(source, cache)
        vm.open(event); runCurrent()
        vm.openId(9); runCurrent()
        response.complete(event); advanceUntilIdle()
        assertEquals(9, vm.state.selected!!.id)
        assertFalse(cache.items.containsKey(event.id))
        assertEquals(1, cache.writes)
    }

    @Test fun disposingSessionClearsTheCache() = runTest(dispatcher) {
        val cache = FakeEventDetailsCache()
        val vm = model(Source { event }, cache)
        vm.open(event); advanceUntilIdle()
        store.clear()
        assertTrue(cache.items.isEmpty())
    }
}

// The real Android LruCache's capacity and expiry are covered by device tests.
internal class FakeEventDetailsCache : EventDetailsCache {
    val items = mutableMapOf<Int, CampusEvent>()
    var lookups = 0
    var writes = 0
    override fun get(id: Int): CampusEvent? { lookups++; return items[id] }
    override fun put(event: CampusEvent) { writes++; items[event.id] = event }
    override fun invalidate(id: Int) { items.remove(id) }
    override fun clear() { items.clear() }
}

package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.auth.SessionRequiredException
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
import org.json.JSONException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CampusEventsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val event = CampusEvent(8, "Meetup", "AI Club", "2026-11-01T14:00:00Z", null, null, false)
    private val fresh = CampusEventsResult(listOf(event), 1, cachedAt = 2000L)
    private var uid: String? = "a"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Source : CampusEventsSource {
        var requests = 0
        var loadResult: suspend (Boolean) -> CampusEventsResult = { error("Set loadResult") }
        var detailResult: suspend () -> CampusEvent = { error("Set detailResult") }
        override suspend fun load(mine: Boolean): CampusEventsResult { requests++; return loadResult(mine) }
        override suspend fun detail(id: Int, source: String, requestId: String?) = detailResult()
    }

    private fun model(source: Source) = CampusEventsViewModel(Application(), source, FakeEventDetailsCache()) { uid }
        .also { store.put("events", it) }

    @Test fun loadingThenNetworkSuccessUpdatesSnapshot() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEventsResult>()
        val vm = model(Source().apply { loadResult = { response.await() } })
        vm.load(false); runCurrent()
        assertTrue(vm.state.loading)
        assertTrue(vm.state.items.isEmpty())
        response.complete(fresh); advanceUntilIdle()
        assertFalse(vm.state.loading)
        assertFalse(vm.state.fromCache)
        assertEquals(fresh.items, vm.state.items)
        assertEquals(2000L, vm.state.cachedAt)
        assertNull(vm.state.error)
    }

    @Test fun offlineRefreshKeepsItemsTotalAndOpenDetails() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh }; detailResult = { event } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        vm.open(event); advanceUntilIdle()
        val response = CompletableDeferred<CampusEventsResult>()
        source.loadResult = { response.await() }
        vm.load(false); runCurrent()
        assertTrue(vm.state.loading)
        assertEquals(fresh.items, vm.state.items)
        assertEquals(1, vm.state.total)
        response.completeExceptionally(IOException("Offline")); advanceUntilIdle()
        assertFalse(vm.state.loading)
        assertTrue(vm.state.fromCache)
        assertEquals(fresh.items, vm.state.items)
        assertEquals(event, vm.state.selected)
        assertNull(vm.state.error)
        assertNull(vm.state.detailError)
    }

    @Test fun initialCachedResponseAndSubsequentOnlineRefreshHaveDifferentSources() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh.copy(fromCache = true, cachedAt = 1000L) } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertTrue(vm.state.fromCache)
        assertEquals(1000L, vm.state.cachedAt)
        source.loadResult = { fresh }
        vm.load(false); advanceUntilIdle()
        assertFalse(vm.state.fromCache)
        assertEquals(2000L, vm.state.cachedAt)
        assertNull(vm.state.error)
    }

    @Test fun olderDiskFallbackDoesNotUndoServerConfirmedCheckIn() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        vm.checkedIn(event.id)
        source.loadResult = { fresh.copy(fromCache = true, cachedAt = 1000L) }
        vm.load(false); advanceUntilIdle()
        assertTrue(vm.state.items.single().checkedIn)
        assertTrue(vm.state.fromCache)
        assertEquals(2000L, vm.state.cachedAt)
    }

    @Test fun noCacheProducesRetryErrorWhichSuccessfulLoadClears() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { throw IOException("Offline") } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertFalse(vm.state.loading)
        assertFalse(vm.state.fromCache)
        assertTrue(vm.state.items.isEmpty())
        assertTrue(vm.state.error.orEmpty().contains("Retry"))
        source.loadResult = { fresh }
        vm.load(false); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
        assertNull(vm.state.error)
    }

    @Test fun successfulEmptySnapshotIsPreservedOnFailedRefresh() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { CampusEventsResult(emptyList(), 0, cachedAt = 1000L) } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        source.loadResult = { throw IOException("Offline") }
        vm.load(false); advanceUntilIdle()
        assertTrue(vm.state.fromCache)
        assertTrue(vm.state.items.isEmpty())
        assertEquals(0, vm.state.total)
        assertNull(vm.state.error)
    }

    @Test fun queryChangeCannotReusePreviousQueryItems() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        source.loadResult = { throw IOException("No mine cache") }
        vm.load(true); advanceUntilIdle()
        assertTrue(vm.state.items.isEmpty())
        assertEquals(0, vm.state.total)
        assertFalse(vm.state.fromCache)
        assertNotNull(vm.state.error)
    }

    @Test fun malformedResponseIsAnErrorRatherThanConnectivityFallback() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        source.loadResult = { throw JSONException("Malformed response") }
        vm.load(false); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
        assertFalse(vm.state.fromCache)
        assertEquals("Malformed response", vm.state.error)
    }

    @Test fun authenticationAndAuthorizationFailuresClearProtectedContent() = runTest(dispatcher) {
        for (failure in listOf(SessionRequiredException(), ApiHttpException(403))) {
            val source = Source().apply { loadResult = { fresh }; detailResult = { event } }
            val vm = model(source)
            vm.load(false); advanceUntilIdle()
            vm.open(event); advanceUntilIdle()
            source.loadResult = { throw failure }
            vm.load(false); advanceUntilIdle()
            assertTrue(vm.state.items.isEmpty())
            assertNull(vm.state.selected)
            assertFalse(vm.state.fromCache)
            assertNotNull(vm.state.error)
        }
    }

    @Test fun accountChangeDiscardsLateResponse() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEventsResult>()
        val vm = model(Source().apply { loadResult = { response.await() } })
        vm.load(false); runCurrent()
        uid = "b"
        response.complete(fresh); advanceUntilIdle()
        assertTrue(vm.state.items.isEmpty())
        assertNull(vm.state.selected)
        assertNotNull(vm.state.error)
    }

    @Test fun duplicateLoadWhileSameQueryIsActiveDoesNotRestartRequest() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEventsResult>()
        val source = Source().apply { loadResult = { response.await() } }
        val vm = model(source)
        vm.load(false); runCurrent()
        vm.load(false); runCurrent()
        assertEquals(1, source.requests)
        response.complete(fresh); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
    }

    @Test fun cancelledOlderQueryCannotReplaceNewerResult() = runTest(dispatcher) {
        val response = CompletableDeferred<CampusEventsResult>()
        val source = Source().apply {
            loadResult = { mine -> if (mine) fresh else withContext(NonCancellable) { response.await() } }
        }
        val vm = model(source)
        vm.load(false); runCurrent()
        vm.load(true); runCurrent()
        response.complete(fresh.copy(items = listOf(event.copy(id = 99)))); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
        assertNull(vm.state.error)
    }

    @Test fun persistenceFailureShowsFreshDataAndWarning() = runTest(dispatcher) {
        val source = Source().apply { loadResult = { fresh.copy(cacheWriteFailed = true) } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
        assertFalse(vm.state.fromCache)
        assertNotNull(vm.state.refreshWarning)
        source.loadResult = { fresh }
        vm.load(false); advanceUntilIdle()
        assertNull(vm.state.refreshWarning)
    }

    @Test fun offlineDetailFailureKeepsPreviewAndDoesNotInventCheckIn() = runTest(dispatcher) {
        val source = Source().apply {
            loadResult = { fresh.copy(fromCache = true) }
            detailResult = { throw IOException("Offline") }
        }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        vm.open(event); advanceUntilIdle()
        assertEquals(event, vm.state.selected)
        assertFalse(vm.state.selected!!.checkedIn)
        assertFalse(vm.state.detailLoading)
        assertNotNull(vm.state.detailError)
        assertNull(vm.state.error)
    }
}

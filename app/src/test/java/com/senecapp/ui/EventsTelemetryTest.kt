package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.data.CampusEvent
import com.senecapp.data.ApiHttpException
import com.senecapp.data.CampusEventsResult
import com.senecapp.data.CampusEventsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EventsTelemetryTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val event = CampusEvent(8, "Event", "Club", "2026-11-01T14:00:00Z", null, null, false)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Source : CampusEventsSource {
        var load: suspend () -> CampusEventsResult = { throw IOException("private") }
        var details: suspend () -> CampusEvent = { throw IOException("private") }
        override suspend fun load(mine: Boolean) = load()
        override suspend fun detail(id: Int, source: String, requestId: String?) = details()
    }
    private fun model(source: Source) = CampusEventsViewModel(Application(), source, FakeEventDetailsCache()) { "a" }
        .also { store.put("events", it) }

    @Test fun originalListFailureHasSafeMetadataAndRetryClearsIt() = runTest(dispatcher) {
        val source = Source()
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertEquals("IOException", vm.state.listFailure!!.errorType)
        assertNotNull(vm.state.error)
        source.load = { CampusEventsResult(listOf(event), 1) }
        vm.load(false); advanceUntilIdle()
        assertNull(vm.state.listFailure)
        assertEquals(listOf(event), vm.state.items)
    }

    @Test fun cachedResponsesAndRetainedOfflineContentAreNotReportedAsErrors() = runTest(dispatcher) {
        val source = Source().apply { load = { CampusEventsResult(listOf(event), 1, fromCache = true) } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertNull(vm.state.listFailure)
        source.load = { throw IOException("Offline") }
        vm.load(false); advanceUntilIdle()
        assertEquals(listOf(event), vm.state.items)
        assertTrue(vm.state.fromCache)
        assertNull(vm.state.error)
        assertNull(vm.state.listFailure)
    }

    @Test fun detailFailureHasMetadataAndMemoryHitDoesNotCreateAnError() = runTest(dispatcher) {
        val source = Source()
        val vm = model(source)
        vm.open(event); advanceUntilIdle()
        assertEquals("IOException", vm.state.detailFailure!!.errorType)
        assertEquals(event, vm.state.selected)
        source.details = { event }
        vm.open(event); advanceUntilIdle()
        assertNull(vm.state.detailFailure)
        source.details = { error("A cache hit must not call the backend") }
        vm.open(event); advanceUntilIdle()
        assertNull(vm.state.detailFailure)
        assertNull(vm.state.detailError)
    }

    @Test fun cancellationProducesNoErrorMetadata() = runTest(dispatcher) {
        val source = Source().apply { load = { throw CancellationException() }; details = { throw CancellationException() } }
        val vm = model(source)
        vm.load(false); vm.open(event); advanceUntilIdle()
        assertNull(vm.state.listFailure)
        assertNull(vm.state.detailFailure)
    }

    @Test fun displayedAuthorizationFailureKeepsItsOriginalTypeWithoutChangingSessionHandling() = runTest(dispatcher) {
        val source = Source().apply { load = { throw ApiHttpException(403) } }
        val vm = model(source)
        vm.load(false); advanceUntilIdle()
        assertEquals("ApiHttpException", vm.state.listFailure!!.errorType)
        assertTrue(vm.state.items.isEmpty())
        assertNotNull(vm.state.error)
    }
}

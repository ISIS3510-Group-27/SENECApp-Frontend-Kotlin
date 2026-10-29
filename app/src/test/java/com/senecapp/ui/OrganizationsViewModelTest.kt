package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.data.*
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
class OrganizationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var uid: String? = "a"
    private val group = Organization(7, "AI Club", "Technology", "technology", 20, null, false)
    private val fresh = OrganizationsResult(listOf(group), listOf("technology" to "Technology"), cachedAt = 2000L)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Source : OrganizationsSource {
        val queries = mutableListOf<OrganizationsQuery>()
        var result: suspend (OrganizationsQuery) -> OrganizationsResult = { error("Set result") }
        var saveResult: suspend () -> Unit = {}
        override suspend fun load(query: OrganizationsQuery): OrganizationsResult { queries += query; return result(query) }
        override suspend fun detail(id: Int, fromSearch: Boolean) = "Description"
        override suspend fun save(id: Int) = saveResult()
        override suspend fun unsave(id: Int) = saveResult()
    }

    private fun model(source: Source) = OrganizationsViewModel(Application(), source) { uid }
        .also { store.put("organizations", it) }

    @Test fun freshLoadUpdatesListCategoriesAndTimestamp() = runTest(dispatcher) {
        val response = CompletableDeferred<OrganizationsResult>()
        val vm = model(Source().apply { result = { response.await() } })
        vm.search("", null); runCurrent()
        assertTrue(vm.state.loading)
        response.complete(fresh); advanceUntilIdle()
        assertEquals(fresh.items, vm.state.items)
        assertEquals(fresh.categories, vm.state.categories)
        assertEquals(2000L, vm.state.cachedAt)
        assertFalse(vm.state.loading)
        assertFalse(vm.state.fromCache)
    }

    @Test fun refreshPreservesTheListDuringLoadingAndAfterNetworkFailure() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        val response = CompletableDeferred<OrganizationsResult>()
        source.result = { response.await() }
        vm.refresh(); runCurrent()
        assertTrue(vm.state.loading)
        assertEquals(fresh.items, vm.state.items)
        response.completeExceptionally(IOException("Offline")); advanceUntilIdle()
        assertFalse(vm.state.loading)
        assertTrue(vm.state.fromCache)
        assertNull(vm.state.error)
        assertEquals(fresh.items, vm.state.items)
        assertEquals(fresh.categories, vm.state.categories)
    }

    @Test fun initialDiskSnapshotIsReplacedAfterConnectionRecovers() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh.copy(fromCache = true, cachedAt = 1000L) } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        assertTrue(vm.state.fromCache)
        assertEquals(1000L, vm.state.cachedAt)
        source.result = { fresh }
        vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.fromCache)
        assertEquals(2000L, vm.state.cachedAt)
    }

    @Test fun changedSearchCannotDisplayThePreviousSearchOnFailure() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        source.result = { throw IOException("Offline") }
        vm.search("sports", null); runCurrent()
        assertTrue(vm.state.items.isEmpty())
        advanceUntilIdle()
        assertFalse(vm.state.fromCache)
        assertTrue(vm.state.items.isEmpty())
        assertTrue(vm.state.error.orEmpty().contains("no saved results"))
    }

    @Test fun filtersAreIncludedWhenRefreshingTheCurrentSearch() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh } }
        val vm = model(source)
        vm.search(" AI ", "technology", true); advanceUntilIdle()
        val filters = GroupFilters("name", true, setOf(1, 2), setOf("ML"))
        vm.filter(filters); advanceUntilIdle()
        assertEquals(OrganizationsQuery("AI", "technology", true, filters), source.queries.last())
        assertTrue(vm.state.searchFiltered)
        assertEquals(filters, vm.state.filters)
    }

    @Test fun duplicateActiveSearchDoesNotRestartTheRequest() = runTest(dispatcher) {
        val response = CompletableDeferred<OrganizationsResult>()
        val source = Source().apply { result = { response.await() } }
        val vm = model(source)
        vm.search("AI", null); runCurrent()
        vm.search("AI", null); runCurrent()
        assertEquals(1, source.queries.size)
        response.complete(fresh); advanceUntilIdle()
    }

    @Test fun olderCancelledQueryCannotOverwriteNewerResults() = runTest(dispatcher) {
        val late = CompletableDeferred<OrganizationsResult>()
        val newer = fresh.copy(items = listOf(group.copy(id = 8)))
        val source = Source().apply { result = { query ->
            if (query.text == "old") withContext(NonCancellable) { late.await() } else newer
        } }
        val vm = model(source)
        vm.search("old", null); runCurrent()
        vm.search("new", null); runCurrent()
        assertEquals(newer.items, vm.state.items)
        late.complete(fresh); advanceUntilIdle()
        assertEquals(newer.items, vm.state.items)
    }

    @Test fun accountChangeDiscardsLateDataAndCategories() = runTest(dispatcher) {
        val late = CompletableDeferred<OrganizationsResult>()
        val vm = model(Source().apply { result = { late.await() } })
        vm.search("", null); runCurrent()
        uid = "b"
        late.complete(fresh); advanceUntilIdle()
        assertTrue(vm.state.items.isEmpty())
        assertTrue(vm.state.categories.isEmpty())
        assertFalse(vm.state.loading)
        assertNotNull(vm.state.error)
    }

    @Test fun authenticationErrorsClearPreviouslyVisibleData() = runTest(dispatcher) {
        for (status in listOf(401, 403)) {
            val source = Source().apply { result = { fresh } }
            val vm = model(source)
            vm.search("", null); advanceUntilIdle()
            source.result = { throw ApiHttpException(status) }
            vm.refresh(); advanceUntilIdle()
            assertTrue(vm.state.items.isEmpty())
            assertTrue(vm.state.categories.isEmpty())
            assertFalse(vm.state.fromCache)
            assertNotNull(vm.state.error)
        }
    }

    @Test fun missingCacheShowsRetryAndCanRecover() = runTest(dispatcher) {
        val source = Source().apply { result = { throw IOException("Offline") } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        assertFalse(vm.state.loading)
        assertTrue(vm.state.error.orEmpty().contains("Retry"))
        source.result = { fresh }
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.state.error)
        assertEquals(fresh.items, vm.state.items)
    }

    @Test fun persistenceFailureKeepsFreshResultsWithAWarning() = runTest(dispatcher) {
        val vm = model(Source().apply { result = { fresh.copy(cacheWriteFailed = true) } })
        vm.search("", null); advanceUntilIdle()
        assertFalse(vm.state.fromCache)
        assertNull(vm.state.error)
        assertNotNull(vm.state.refreshWarning)
        assertEquals(fresh.items, vm.state.items)
    }

    @Test fun oldDiskFallbackCannotUndoAConfirmedSaveInMemory() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        vm.toggleSave(group.id); advanceUntilIdle()
        assertTrue(vm.state.items.single().isSaved)
        source.result = { fresh.copy(fromCache = true, cachedAt = 1000L) }
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.items.single().isSaved)
        assertTrue(vm.state.fromCache)
        assertEquals(2000L, vm.state.cachedAt)
    }

    @Test fun failedSaveRollsBackInsteadOfClaimingAnOfflineSuccess() = runTest(dispatcher) {
        val source = Source().apply { result = { fresh }; saveResult = { throw IOException("Offline") } }
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        vm.toggleSave(group.id)
        assertTrue(vm.state.items.single().isSaved)
        advanceUntilIdle()
        assertFalse(vm.state.items.single().isSaved)
        assertNull(vm.state.savingId)
        assertNotNull(vm.state.saveError)
    }

    @Test fun missingSessionCannotLoadData() = runTest(dispatcher) {
        uid = null
        val source = Source()
        val vm = model(source)
        vm.search("", null); advanceUntilIdle()
        assertTrue(source.queries.isEmpty())
        assertFalse(vm.state.loading)
        assertTrue(vm.state.items.isEmpty())
    }
}

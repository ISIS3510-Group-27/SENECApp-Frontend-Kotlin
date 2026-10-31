package com.senecapp.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.senecapp.auth.IdTokenProvider
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
class GroupDetailsMemoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var uid: String? = "a"
    private data class Request(val path: String, val method: String, val body: String?)
    private val requests = mutableListOf<Request>()
    private var response: suspend (Request) -> ApiResponse = { ApiResponse(200, body()) }
    private fun body(id: Int = 1, name: String = "Club", member: Boolean = false) =
        """{"id":$id,"name":"$name","description":"About","category":{"slug":"technology","label":"Technology"},"is_member":$member,"member_count":10}"""
    private val group get() = parseGroupDetails(org.json.JSONObject(body()))

    private class Cache : GroupDetailsCache {
        val items = mutableMapOf<Int, GroupDetails>()
        var lookups = 0
        override fun get(id: Int): GroupDetails? { lookups++; return items[id] }
        override fun put(detail: GroupDetails) { items[detail.group.id] = detail }
        override fun invalidate(id: Int) { items.remove(id) }
        override fun clear() { items.clear() }
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model(cache: Cache = Cache(), key: String = "home"): CampusHomeViewModel {
        val tokens = object : IdTokenProvider {
            override fun userId() = uid
            override suspend fun token(forceRefresh: Boolean) = "test-token"
            override fun invalidate(expectedUserId: String) { if (uid == expectedUserId) uid = null }
        }
        val api = AuthenticatedApiClient(tokens) { path, method, body, _, _ ->
            val request = Request(path, method, body)
            requests += request
            response(request)
        }
        return CampusHomeViewModel(Application(), CampusRepository(api), cache, { uid }, EventsTestPreferences().value)
            .also { store.put(key, it) }
    }

    @Test fun missCachesResultAndHitDisplaysItBeforeFreshResponse() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        vm.openGroup(1); advanceUntilIdle()
        assertEquals(group, cache.items[1])
        vm.closeGroup()
        val updated = CompletableDeferred<ApiResponse>()
        response = { updated.await() }
        vm.openGroup(1)
        assertEquals(group, vm.detail)
        assertTrue(vm.detailFromCache)
        assertTrue(vm.detailLoading)
        runCurrent()
        assertEquals(2, requests.size)
        updated.complete(ApiResponse(200, body(name = "Updated"))); advanceUntilIdle()
        assertEquals("Updated", vm.detail!!.group.name)
        assertFalse(vm.detailFromCache)
        assertFalse(vm.detailLoading)
    }

    @Test fun everyActualOpenPreservesSourceAndRecommendationIdEvenOnHits() = runTest(dispatcher) {
        val vm = model()
        for (source in listOf("direct", "explore", "search", "recommendation", "notification", "event")) {
            vm.openGroup(1, source, "request 1"); advanceUntilIdle(); vm.closeGroup()
            assertEquals("/groups/1?entry_point=$source&rec_request_id=request+1", requests.last().path)
        }
        assertEquals(6, requests.size)
    }

    @Test fun failedRefreshKeepsOnlyAnUnexpiredReadOnlyPreview() = runTest(dispatcher) {
        val cache = Cache().apply { put(group) }
        val vm = model(cache)
        response = { throw IOException("Offline") }
        vm.openGroup(1); advanceUntilIdle()
        assertEquals(group, vm.detail)
        assertTrue(vm.detailFromCache)
        assertFalse(vm.detailLoading)
        assertNotNull(vm.error)
        vm.join("", {}); vm.toggleGroupSave({}); advanceUntilIdle()
        assertEquals(1, requests.size)
    }

    @Test fun previewExpiringDuringFailedRefreshIsRemoved() = runTest(dispatcher) {
        val cache = Cache().apply { put(group) }
        val delayed = CompletableDeferred<ApiResponse>()
        response = { delayed.await() }
        val vm = model(cache)
        vm.openGroup(1); runCurrent()
        cache.items.clear()
        delayed.completeExceptionally(IOException("Offline")); advanceUntilIdle()
        assertNull(vm.detail)
        assertFalse(vm.detailFromCache)
    }

    @Test fun failedMissIsNotCachedAndRetryCanRecover() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        response = { throw IOException("Offline") }
        vm.openGroup(1, "search"); advanceUntilIdle()
        assertTrue(cache.items.isEmpty()); assertNull(vm.detail)
        response = { ApiResponse(200, body()) }
        vm.retryGroup(); advanceUntilIdle()
        assertEquals("/groups/1?entry_point=search", requests.last().path)
        assertEquals(group, vm.detail); assertNull(vm.error)
    }

    @Test fun authenticationErrorsClearAllCachedAndVisibleDetails() = runTest(dispatcher) {
        for (status in listOf(401, 403)) {
            uid = "a"
            val cache = Cache().apply { put(group) }
            val vm = model(cache)
            response = { ApiResponse(status, "") }
            vm.openGroup(1); advanceUntilIdle()
            assertNull(vm.detail); assertTrue(cache.items.isEmpty())
            assertFalse(vm.detailFromCache); assertFalse(vm.detailLoading)
        }
    }

    @Test fun accountChangeOrLogoutCannotReadPreviousCache() = runTest(dispatcher) {
        for (next in listOf("b", null)) {
            uid = "a"
            val cache = Cache().apply { put(group) }
            val vm = model(cache)
            val count = requests.size
            uid = next
            vm.openGroup(1); advanceUntilIdle()
            assertEquals(0, cache.lookups); assertTrue(cache.items.isEmpty())
            assertNull(vm.detail); assertEquals(count, requests.size)
        }
    }

    @Test fun lateResponseAfterAccountChangeCannotPopulateCache() = runTest(dispatcher) {
        val cache = Cache()
        val delayed = CompletableDeferred<ApiResponse>()
        response = { delayed.await() }
        val vm = model(cache)
        vm.openGroup(1); runCurrent()
        uid = "b"
        delayed.complete(ApiResponse(200, body())); advanceUntilIdle()
        assertNull(vm.detail); assertTrue(cache.items.isEmpty())
    }

    @Test fun cancelledOldResponseCannotOverwriteNewGroupOrPopulateCache() = runTest(dispatcher) {
        val cache = Cache()
        val delayed = CompletableDeferred<ApiResponse>()
        response = { if (it.path.startsWith("/groups/1?")) withContext(NonCancellable) { delayed.await() }
            else ApiResponse(200, body(2)) }
        val vm = model(cache)
        vm.openGroup(1); runCurrent()
        vm.openGroup(2); runCurrent()
        delayed.complete(ApiResponse(200, body())); advanceUntilIdle()
        assertEquals(2, vm.detail!!.group.id)
        assertFalse(cache.items.containsKey(1)); assertTrue(cache.items.containsKey(2))
    }

    @Test fun saveInvalidatesCacheAndChangesVisibleSavedFlag() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        vm.openGroup(1); advanceUntilIdle()
        vm.toggleGroupSave({}); advanceUntilIdle()
        assertTrue(vm.detail!!.group.isSaved)
        assertFalse(cache.items.containsKey(1))
        assertEquals("PUT", requests.last().method)
        vm.openGroup(1)
        assertNull(vm.detail)
        advanceUntilIdle()
    }

    @Test fun joinSendsTheAttemptIdOfTheOpenedForm() = runTest(dispatcher) {
        val vm = model()
        vm.openGroup(1); advanceUntilIdle()
        vm.join("", {}, "3b2f1c4e-8d7a-4f61-9a55-0c9b8e7d6a51"); advanceUntilIdle()
        val json = org.json.JSONObject(requests.single { it.method == "POST" }.body!!)
        assertEquals("3b2f1c4e-8d7a-4f61-9a55-0c9b8e7d6a51", json.getString("join_attempt_id"))
    }

    @Test fun joinAndLeaveInvalidateAndKeepJoinAttribution() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        vm.openGroup(1, "recommendation", "request"); advanceUntilIdle()
        var callbacks = 0
        vm.join("Hello", { callbacks++ }); advanceUntilIdle()
        val join = requests.single { it.method == "POST" }
        val json = org.json.JSONObject(join.body!!)
        assertEquals("recommendation", json.getString("entry_point"))
        assertEquals("request", json.getString("rec_request_id"))
        assertEquals(1, callbacks); assertFalse(cache.items.containsKey(1))
        vm.openGroup(1); advanceUntilIdle()
        vm.leave({ callbacks++ }); advanceUntilIdle()
        assertTrue(requests.any { it.method == "DELETE" && it.path == "/groups/1/membership" })
        assertFalse(cache.items.containsKey(1)); assertEquals(2, callbacks)
    }

    @Test fun eventCreationAndAdminRequestInvalidateGroupData() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        vm.openGroup(1); advanceUntilIdle()
        vm.createEvent(org.json.JSONObject(), {}); advanceUntilIdle()
        assertFalse(cache.items.containsKey(1))
        vm.openGroup(1); advanceUntilIdle()
        vm.requestAdmin("Hello"); advanceUntilIdle()
        assertFalse(cache.items.containsKey(1))
        assertTrue(requests.any { it.path == "/groups/1/events" && it.method == "POST" })
        assertTrue(requests.any { it.path == "/groups/1/admin-requests" && it.method == "POST" })
    }

    @Test fun preMutationResponseCannotRestoreOldSavedFlag() = runTest(dispatcher) {
        val cache = Cache()
        val delayed = CompletableDeferred<ApiResponse>()
        val vm = model(cache)
        vm.openGroup(1); advanceUntilIdle()
        // A second open previews the cached value but cannot enable a mutation until validated.
        response = { delayed.await() }
        vm.openGroup(1); runCurrent()
        vm.toggleGroupSave({}); runCurrent()
        assertFalse(requests.any { it.method == "PUT" })
        delayed.complete(ApiResponse(200, body())); advanceUntilIdle()
        response = { ApiResponse(200, body()) }
        vm.toggleGroupSave({}); advanceUntilIdle()
        assertTrue(vm.detail!!.group.isSaved); assertTrue(cache.items.isEmpty())
    }

    @Test fun sessionDisposalClearsCache() = runTest(dispatcher) {
        val cache = Cache()
        val vm = model(cache)
        vm.openGroup(1); advanceUntilIdle()
        assertFalse(cache.items.isEmpty())
        store.clear()
        assertTrue(cache.items.isEmpty())
    }
}

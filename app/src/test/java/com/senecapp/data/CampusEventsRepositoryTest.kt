package com.senecapp.data

import com.senecapp.auth.IdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CampusEventsRepositoryTest {
    private val event = CampusEvent(99, "Cached", "Club", "2026-11-01T14:00:00Z", null, null, false)
    private val snapshot = CachedEvents(listOf(event), 1, 1000L)

    private class Tokens : IdTokenProvider {
        var uid: String? = "a"
        var failure: Exception? = null
        override fun userId() = uid
        override suspend fun token(forceRefresh: Boolean): String { failure?.let { throw it }; return "token" }
        override fun invalidate(expectedUserId: String) { if (uid == expectedUserId) uid = null }
    }

    private class Cache : EventsCache {
        val entries = mutableMapOf<Pair<String, Boolean>, CachedEvents>()
        var reads = 0
        var writes = 0
        var writeSucceeds = true
        override fun read(uid: String, mine: Boolean): CachedEvents? { reads++; return entries[uid to mine] }
        override fun save(uid: String, mine: Boolean, snapshot: CachedEvents): Boolean {
            writes++
            if (writeSucceeds) entries[uid to mine] = snapshot
            return writeSucceeds
        }
    }

    private fun cache() = Cache().apply { entries["a" to false] = snapshot }
    private fun repository(dispatcher: CoroutineDispatcher, cache: Cache, tokens: Tokens = Tokens(),
        clock: () -> Long = { 2000L }, response: suspend (String) -> ApiResponse): CampusEventsRepository =
        CampusEventsRepository(cache, AuthenticatedApiClient(tokens) { path, _, _, _, _ -> response(path) },
            tokens::userId, dispatcher, clock)

    private fun page(id: Int, total: Int = 1) = ApiResponse(200,
        """{"items":[{"id":$id,"title":"Fresh $id","starts_at":"2026-11-01T14:00:00Z"}],"total":$total}""")

    @Test fun fetchesAllPagesBeforeReplacingCache() = runTest {
        val cache = cache()
        val paths = mutableListOf<String>()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { path ->
            paths += path
            assertEquals(snapshot, cache.entries["a" to false])
            assertEquals(0, cache.writes)
            page(paths.size, 2)
        }
        val result = repo.load(false)
        assertEquals(listOf("/events?mine=false&limit=100&offset=0", "/events?mine=false&limit=100&offset=1"), paths)
        assertEquals(listOf(1, 2), result.items.map { it.id })
        assertFalse(result.fromCache)
        assertEquals(2000L, result.cachedAt)
        assertEquals(CachedEvents(result.items, 2, 2000L), cache.entries["a" to false])
        assertEquals(1, cache.writes)
        assertEquals(0, cache.reads)
    }

    @Test fun laterPageNetworkFailureReturnsOldCompleteSnapshotWithoutWriting() = runTest {
        val cache = cache()
        var requests = 0
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            if (++requests == 1) page(1, 2) else throw IOException("Disconnected")
        }
        val result = repo.load(false)
        assertEquals(snapshot.items, result.items)
        assertTrue(result.fromCache)
        assertEquals(snapshot.updatedAt, result.cachedAt)
        assertEquals(snapshot, cache.entries["a" to false])
        assertEquals(0, cache.writes)
    }

    @Test fun tokenConnectivityFailureAlsoUsesCache() = runTest {
        val cache = cache()
        val tokens = Tokens().apply { failure = IOException("Offline") }
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens) { error("No token means no HTTP request") }
        assertTrue(repo.load(false).fromCache)
        assertEquals(0, cache.writes)
    }

    @Test fun anotherUserOrQueryCannotUseTheExistingCache() = runTest {
        val cache = cache()
        val tokens = Tokens()
        val offline = IOException("Offline")
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens) { throw offline }
        expect<IOException> { repo.load(true) }
        tokens.uid = "b"
        expect<IOException> { repo.load(false) }
        assertEquals(snapshot, cache.entries["a" to false])
        assertEquals(0, cache.writes)
    }

    @Test fun httpAndAuthenticationFailuresDoNotFallBack() = runTest {
        for (status in listOf(401, 403, 500)) {
            val cache = cache()
            val repo = repository(StandardTestDispatcher(testScheduler), cache) { ApiResponse(status, "") }
            if (status == 401) expect<SessionRequiredException> { repo.load(false) }
            else expect<ApiHttpException> { repo.load(false) }
            assertEquals(0, cache.reads)
            assertEquals(0, cache.writes)
        }
    }

    @Test fun malformedAndIncompleteResponsesDoNotReadOrReplaceCache() = runTest {
        val cache = cache()
        val malformed = repository(StandardTestDispatcher(testScheduler), cache) { ApiResponse(200, "not json") }
        expect<JSONException> { malformed.load(false) }
        var requests = 0
        val incomplete = repository(StandardTestDispatcher(testScheduler), cache) {
            if (++requests == 1) page(1, 2) else ApiResponse(200, """{"items":[],"total":2}""")
        }
        expect<IllegalStateException> { incomplete.load(false) }
        assertEquals(snapshot, cache.entries["a" to false])
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun successfulEmptyResponseReplacesAnOldSnapshot() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { ApiResponse(200, """{"items":[],"total":0}""") }
        val result = repo.load(false)
        assertFalse(result.fromCache)
        assertTrue(result.items.isEmpty())
        assertEquals(CachedEvents(emptyList(), 0, 2000L), cache.entries["a" to false])
    }

    @Test fun failedPersistenceStillReturnsFreshNetworkData() = runTest {
        val cache = cache().apply { writeSucceeds = false }
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { page(1) }
        val result = repo.load(false)
        assertFalse(result.fromCache)
        assertTrue(result.cacheWriteFailed)
        assertEquals(1, result.items.single().id)
        assertEquals(0, cache.reads)
    }

    @Test fun accountChangeDuringNetworkFailureCannotReadEitherUsersCache() = runTest {
        val cache = cache()
        val tokens = Tokens()
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens) {
            tokens.uid = "b"
            throw IOException("Offline")
        }
        expect<SessionChangedException> { repo.load(false) }
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun accountChangeAfterParsingCannotSaveTheResponse() = runTest {
        val cache = cache()
        val tokens = Tokens()
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens,
            clock = { tokens.uid = "b"; 2000L }) { page(1) }
        expect<SessionChangedException> { repo.load(false) }
        assertEquals(0, cache.writes)
    }

    @Test fun cancellationIsNotAConnectivityFailure() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { throw CancellationException("Cancelled") }
        expect<CancellationException> { repo.load(false) }
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun cancelledRequestCannotWriteEvenIfTransportReturnsAResponse() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            currentCoroutineContext().cancel()
            page(1)
        }
        val job = launch { repo.load(false); fail("Cancelled load must not return") }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun missingSessionDoesNotReadCacheOrCallBackend() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache, Tokens().apply { uid = null }) {
            error("No session means no request")
        }
        expect<SessionRequiredException> { repo.load(false) }
        assertEquals(0, cache.reads)
    }

    @Test fun eventDetailsRemainNetworkOnly() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { throw IOException("Offline") }
        expect<IOException> { repo.detail(event.id) }
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    private suspend inline fun <reified T : Exception> expect(block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Exception) { assertTrue("Unexpected $failure", failure is T) }
    }
}

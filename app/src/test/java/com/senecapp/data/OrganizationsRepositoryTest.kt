package com.senecapp.data

import com.senecapp.auth.IdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationsRepositoryTest {
    private val query = OrganizationsQuery()
    private val group = Organization(7, "Saved", "Technology", "technology", 20, null, false)
    private val snapshot = CachedOrganizations(listOf(group), listOf("technology" to "Technology"), 1, 1000L)
    private val categories = ApiResponse(200, """[{"slug":"technology","label":"Technology"}]""")

    private class Tokens : IdTokenProvider {
        var uid: String? = "a"
        var failure: Exception? = null
        override fun userId() = uid
        override suspend fun token(forceRefresh: Boolean): String { failure?.let { throw it }; return "token" }
        override fun invalidate(expectedUserId: String) { if (uid == expectedUserId) uid = null }
    }

    private class Cache : OrganizationsCache {
        val entries = mutableMapOf<Pair<String, String>, CachedOrganizations>()
        var reads = 0
        var writes = 0
        var writeSucceeds = true
        override fun read(uid: String, query: OrganizationsQuery): CachedOrganizations? { reads++; return entries[uid to query.key()] }
        override fun save(uid: String, query: OrganizationsQuery, snapshot: CachedOrganizations): Boolean {
            writes++
            if (writeSucceeds) entries[uid to query.key()] = snapshot
            return writeSucceeds
        }
    }

    private fun cache() = Cache().apply { entries["a" to query.key()] = snapshot }
    private fun repository(dispatcher: CoroutineDispatcher, cache: Cache, tokens: Tokens = Tokens(),
        response: suspend (String) -> ApiResponse): OrganizationsRepository = OrganizationsRepository(cache,
        AuthenticatedApiClient(tokens) { path, _, _, _, _ -> response(path) }, tokens::userId, dispatcher) { 2000L }

    private fun page(id: Int = 1, total: Int = 1) = ApiResponse(200,
        """{"items":[{"id":$id,"name":"Fresh $id","category":{"slug":"technology","label":"Technology"},"member_count":20,"next_event":null}],"total":$total}""")

    @Test fun allPagesAndCategoriesMustCompleteBeforeSaving() = runTest {
        val cache = cache()
        val paths = mutableListOf<String>()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) { path ->
            paths += path
            assertEquals(0, cache.writes)
            assertEquals(snapshot, cache.entries["a" to query.key()])
            if (path == "/categories") categories else page(paths.size, 2)
        }
        val result = repo.load(query)
        assertEquals(listOf("/groups?limit=100&sort=popular&offset=0", "/groups?limit=100&sort=popular&offset=1", "/categories"), paths)
        assertEquals(listOf(1, 2), result.items.map { it.id })
        assertEquals(snapshot.categories, result.categories)
        assertFalse(result.fromCache)
        assertEquals(1, cache.writes)
        assertEquals(2000L, cache.entries["a" to query.key()]!!.updatedAt)
    }

    @Test fun laterPageFailureReturnsOldCompleteSnapshot() = runTest {
        val cache = cache()
        var calls = 0
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            if (++calls == 1) page(1, 2) else throw IOException("Offline")
        }
        val result = repo.load(query)
        assertTrue(result.fromCache)
        assertEquals(snapshot.items, result.items)
        assertEquals(snapshot.categories, result.categories)
        assertEquals(1000L, result.cachedAt)
        assertEquals(0, cache.writes)
    }

    @Test fun categoriesFailureDoesNotPersistAPartialSnapshot() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            if (it == "/categories") throw IOException("Offline") else page()
        }
        assertEquals(snapshot.items, repo.load(query).items)
        assertEquals(0, cache.writes)
    }

    @Test fun noTokenConnectionUsesOnlyTheSameAccountsAndQueriesSnapshot() = runTest {
        val cache = cache()
        val tokens = Tokens().apply { failure = IOException("Offline") }
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens) { error("No HTTP expected") }
        assertTrue(repo.load(query).fromCache)
        expect<IOException> { repo.load(query.copy(category = "technology")) }
        tokens.uid = "b"
        expect<IOException> { repo.load(query) }
        assertEquals(0, cache.writes)
    }

    @Test fun httpErrorsDoNotReadOrReplaceLocalData() = runTest {
        for (status in listOf(401, 403, 500)) {
            val cache = cache()
            val repo = repository(StandardTestDispatcher(testScheduler), cache) { ApiResponse(status, "") }
            if (status == 401) expect<SessionRequiredException> { repo.load(query) }
            else expect<ApiHttpException> { repo.load(query) }
            assertEquals(0, cache.reads)
            assertEquals(0, cache.writes)
        }
    }

    @Test fun malformedDuplicateAndIncompleteResultsCannotReplaceCache() = runTest {
        val responses = listOf(ApiResponse(200, "invalid JSON"), ApiResponse(200, """{"items":[],"total":2}"""),
            page(total = 2))
        for (response in responses) {
            val cache = cache()
            val repo = repository(StandardTestDispatcher(testScheduler), cache) { response }
            expect<Exception> { repo.load(query) }
            assertEquals(0, cache.reads)
            assertEquals(0, cache.writes)
        }
    }

    @Test fun emptySuccessReplacesOldResultsAndFailureToWriteStillReturnsFreshData() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            if (it == "/categories") categories else ApiResponse(200, """{"items":[],"total":0}""")
        }
        assertTrue(repo.load(query).items.isEmpty())
        assertTrue(cache.entries["a" to query.key()]!!.items.isEmpty())
        cache.writeSucceeds = false
        assertTrue(repo.load(query).cacheWriteFailed)
    }

    @Test fun filtersAreEncodedAndKeptInTheCacheKey() = runTest {
        val cache = cache()
        val filtered = OrganizationsQuery(" AI & ML ", "technology", true,
            GroupFilters("name", true, linkedSetOf(3, 1), linkedSetOf("SD", "ML")))
        val paths = mutableListOf<String>()
        val repo = repository(StandardTestDispatcher(testScheduler), cache) {
            paths += it
            if (it == "/categories") categories else page()
        }
        repo.load(filtered)
        assertEquals("/groups?limit=100&q=AI+%26+ML&category=technology&has_upcoming_events=true&sort=name&verified=true&interest_id=1&interest_id=3&building=ML&building=SD&offset=0", paths.first())
        assertEquals(snapshot, cache.entries["a" to query.key()])
        assertNotNull(cache.entries["a" to filtered.key()])
    }

    @Test fun accountChangeDuringFailureCannotReadCache() = runTest {
        val cache = cache()
        val tokens = Tokens()
        val repo = repository(StandardTestDispatcher(testScheduler), cache, tokens) {
            tokens.uid = "b"; throw IOException("Offline")
        }
        expect<SessionChangedException> { repo.load(query) }
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun cancelledRequestCannotWriteALateResponse() = runTest {
        val cache = cache()
        val job = launch {
            val repo = repository(StandardTestDispatcher(testScheduler), cache) {
                currentCoroutineContext().cancel()
                page()
            }
            repo.load(query)
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    @Test fun missingSessionNeverCallsNetworkOrReadsCache() = runTest {
        val cache = cache()
        val repo = repository(StandardTestDispatcher(testScheduler), cache, Tokens().apply { uid = null }) { error("No HTTP expected") }
        expect<SessionRequiredException> { repo.load(query) }
        assertEquals(0, cache.reads)
        assertEquals(0, cache.writes)
    }

    private suspend inline fun <reified T : Exception> expect(block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.simpleName}") } catch (failure: Exception) { if (failure !is T) throw failure }
    }
}

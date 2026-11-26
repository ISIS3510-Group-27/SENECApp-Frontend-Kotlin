package com.senecapp.ui

import com.senecapp.auth.SessionRequiredException
import com.senecapp.data.ApiHttpException
import com.senecapp.data.GroupRecommendations
import com.senecapp.data.GroupRecommendationsRepository
import com.senecapp.data.RecommendationStorage
import com.senecapp.data.cacheableRecommendationImage
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRecommendationLoadTest {
    private val fresh = GroupRecommendations("fresh", emptyList())
    private val cached = GroupRecommendations("", emptyList())

    private class Cache(
        val readResult: suspend () -> GroupRecommendations? = { null },
        val writeResult: () -> Unit = {},
    ) : RecommendationStorage {
        val writes = mutableListOf<Pair<String, GroupRecommendations>>()
        override suspend fun read(uid: String) = readResult()
        override suspend fun save(uid: String, result: GroupRecommendations) {
            writeResult()
            writes += uid to result
        }
    }

    @Test fun startsBothChildrenAndPrefersTheNetworkResult() = runTest {
        var reading = false
        var fetching = false
        var readCancelled = false
        val networkResult = CompletableDeferred<GroupRecommendations>()
        val cache = Cache(readResult = {
            reading = true
            try { awaitCancellation() } finally { readCancelled = true }
        })
        val result = async {
            loadProfileRecommendations("user", cache, { fetching = true; networkResult.await() }) { true }
        }
        runCurrent()
        assertTrue(reading && fetching)
        networkResult.complete(fresh)
        assertEquals(RecommendationLoad(fresh, false), result.await())
        assertTrue(readCancelled)
        assertEquals(listOf("user" to fresh), cache.writes)
    }

    @Test fun connectionFailureReturnsCacheWithoutReplacingIt() = runTest {
        val cache = Cache(readResult = { cached })
        val result = loadProfileRecommendations("user", cache, { throw IOException() }) { true }
        assertEquals(RecommendationLoad(cached, true), result)
        assertTrue(cache.writes.isEmpty())
    }

    @Test fun cacheFailureDoesNotHideFreshRecommendations() = runTest {
        val cache = Cache(readResult = { error("broken read") }, writeResult = { error("broken write") })
        assertEquals(RecommendationLoad(fresh, false),
            loadProfileRecommendations("user", cache, { fresh }) { true })
    }

    @Test fun persistsPublicImageReferencesWithoutSavingCredentialBearingUrls() {
        assertEquals("/media/cover.png", cacheableRecommendationImage("/media/cover.png"))
        assertEquals("https://images.example/cover.png", cacheableRecommendationImage("https://images.example/cover.png"))
        assertNull(cacheableRecommendationImage("https://images.example/cover.png?token=secret"))
        assertNull(cacheableRecommendationImage("https://user:secret@images.example/cover.png"))
    }

    @Test fun authorizationFailureNeverReturnsStoredRecommendations() = runTest {
        val result = runCatching {
            loadProfileRecommendations("user", Cache(readResult = { cached }), { throw ApiHttpException(403) }) { true }
        }
        assertTrue(result.exceptionOrNull() is ApiHttpException)
    }

    @Test fun cancelledParentCancelsBothChildrenAndDoesNotWrite() = runTest {
        var diskCancelled = false
        var networkCancelled = false
        val cache = Cache(readResult = { try { awaitCancellation() } finally { diskCancelled = true } })
        val load = async {
            loadProfileRecommendations("user", cache,
                { try { awaitCancellation() } finally { networkCancelled = true } }) { true }
        }
        runCurrent()
        load.cancelAndJoin()
        assertTrue(diskCancelled && networkCancelled)
        assertTrue(cache.writes.isEmpty())
    }

    @Test fun accountChangeDuringLoadPreventsWritingAndPublishing() = runTest {
        val cache = Cache()
        var ownsSession = true
        val result = runCatching {
            loadProfileRecommendations("user", cache, { ownsSession = false; fresh }) { ownsSession }
        }
        assertTrue(result.exceptionOrNull() is SessionRequiredException)
        assertTrue(cache.writes.isEmpty())
    }

    @Test fun olderResponseCannotReplaceNewerResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val slowResponse = CompletableDeferred<GroupRecommendations>()
            val cache = Cache()
            var requests = 0
            val vm = GroupRecommendationsViewModel(GroupRecommendationsRepository(), cache, { "user" }) {
                if (requests++ == 0) withContext(NonCancellable) { slowResponse.await() } else fresh
            }
            vm.load()
            runCurrent()
            vm.load()
            runCurrent()
            assertEquals(fresh, vm.state.result)
            slowResponse.complete(GroupRecommendations("old", emptyList()))
            advanceUntilIdle()
            assertEquals(fresh, vm.state.result)
            assertEquals(listOf("user" to fresh), cache.writes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun comparisonReusesAnInFlightLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<GroupRecommendations>()
            val cache = Cache()
            var requests = 0
            val vm = GroupRecommendationsViewModel(GroupRecommendationsRepository(), cache, { "user" }) {
                requests++
                response.await()
            }
            vm.load()
            runCurrent()
            vm.loadForComparison()
            runCurrent()
            assertEquals(1, requests)
            response.complete(fresh)
            advanceUntilIdle()
            assertEquals(fresh, vm.state.result)
            assertEquals(listOf("user" to fresh), cache.writes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun onlyFreshSuccessfulRecommendationsAllowOpeningDetails() {
        val loaded = GroupRecommendationsUiState(loading = false, result = fresh)
        assertTrue(loaded.canOpenComparisonGroup(online = true))
        assertFalse(loaded.canOpenComparisonGroup(online = false))
        assertFalse(loaded.copy(result = cached, fromCache = true).canOpenComparisonGroup(online = true))
        assertFalse(loaded.copy(loading = true).canOpenComparisonGroup(online = true))
        assertFalse(loaded.copy(error = "error").canOpenComparisonGroup(online = true))
        assertFalse(loaded.copy(result = null).canOpenComparisonGroup(online = true))
    }
}

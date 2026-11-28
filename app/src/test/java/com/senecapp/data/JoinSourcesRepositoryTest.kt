package com.senecapp.data

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinSourcesRepositoryTest {
    private val key = JoinSourcesKey("leader", 7)
    private val data = JoinSources("Question", "Answer", "Campus", 90, 12, 3, emptyList())
    private var now = JOIN_VISIT_INTERVAL_MS + 50
    private val saved = JoinSourcesVisit(50, data.copy(joins = 1))
    private val disk = object : JoinSourcesStorage {
        val records = mutableMapOf(key to listOf(saved))
        var onHistory = {}
        override fun history(key: JoinSourcesKey) = records[key].orEmpty().also { onHistory() }
        override fun save(key: JoinSourcesKey, visit: JoinSourcesVisit) { records[key] = listOf(visit) }
        override fun clear(key: JoinSourcesKey) { records.remove(key) }
    }
    private val memory = object : JoinSourcesMemory {
        val entries = mutableMapOf<JoinSourcesKey, JoinSourcesView>()
        override fun get(key: JoinSourcesKey) = entries[key]
        override fun put(key: JoinSourcesKey, value: JoinSourcesView) { entries[key] = value }
        override fun remove(key: JoinSourcesKey) { entries.remove(key) }
    }

    @Test fun startsNetworkBeforeReadingDiskThenEmitsSavedAndFresh() = runTest {
        val response = CompletableDeferred<JoinSources>()
        var started = false
        disk.onHistory = { assertTrue(started) }
        val repo = repository(StandardTestDispatcher(testScheduler)) { started = true; response.await() }
        val values = mutableListOf<JoinSourcesView>()
        val job = launch { repo.watch(key, online = true, force = false).toList(values) }
        runCurrent()
        assertEquals(listOf(JoinSourcesView(saved, saved, fromPhone = true)), values)
        response.complete(data)
        job.join()
        assertEquals(JoinSourcesView(JoinSourcesVisit(now, data), saved, fromPhone = false), values.last())
        assertEquals(values.last().current, disk.history(key).single())
    }

    @Test fun memoryHitSkipsRequestUntilTwoMinutesElapse() = runTest {
        var requests = 0
        val repo = repository(StandardTestDispatcher(testScheduler)) { requests++; data }
        repo.watch(key, true, false).toList()
        assertEquals(data, repo.watch(key, true, false).toList().single().current.data)
        assertEquals(1, requests)
        now += 120_000L
        assertEquals(2, repo.watch(key, true, false).toList().size)
        assertEquals(2, requests)
    }

    @Test fun offlineAvoidsNetworkAndUsesOnlyTheMatchingCopy() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler)) { error("Unexpected network request") }
        assertEquals(saved, repo.watch(key, online = false, force = true).toList().single().current)
        val failure = runCatching { repo.watch(key.copy(groupId = 8), false, true).toList() }.exceptionOrNull()
        assertTrue(failure is JoinSourcesOffline)
    }

    @Test fun onlineFailureKeepsTheEmittedCopyAndReportsTheError() = runTest {
        val failure = IOException("Unavailable")
        val repo = repository(StandardTestDispatcher(testScheduler)) { throw failure }
        val values = mutableListOf<JoinSourcesView>()
        assertSame(failure, runCatching { repo.watch(key, true, true).toList(values) }.exceptionOrNull())
        assertEquals(saved, values.single().current)
    }

    @Test fun accessDeniedPurgesOnlyThatGroupsSnapshots() = runTest {
        val other = key.copy(groupId = 8)
        disk.records[other] = listOf(saved)
        val repo = repository(StandardTestDispatcher(testScheduler)) { throw ApiHttpException(403) }
        val error = runCatching { repo.watch(key, true, true).toList() }.exceptionOrNull()
        assertTrue(error is ApiHttpException && error.status == 403)
        assertTrue(disk.history(key).isEmpty())
        assertEquals(listOf(saved), disk.history(other))
    }

    private fun repository(io: kotlinx.coroutines.CoroutineDispatcher, fetch: suspend (Int) -> JoinSources) =
        JoinSourcesRepository(disk, fetch, { key.account }, memory, { now }, io)
}

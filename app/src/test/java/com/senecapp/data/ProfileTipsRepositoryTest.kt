package com.senecapp.data

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTipsRepositoryTest {
    private val field = ProfileTipField("has_image", false, 10, 0.5, 1.5, 0.3, 0.2)
    private val tips = ProfileTips("Question", "Answer", 90, 0, 1, listOf(field))
    private var now = 1_000L
    private var uid: String? = "leader"
    private val logs = mutableListOf<String>()

    // HashMap fake: the real LRU eviction is Android's; the expiry and account rules live in the repository.
    private val memory = object : ProfileTipsMemory {
        val entries = mutableMapOf<ProfileTipsKey, ProfileTipsEntry>()
        var owner: String? = null
        override fun get(key: ProfileTipsKey) = entries[key]
        override fun put(key: ProfileTipsKey, entry: ProfileTipsEntry) { entries[key] = entry }
        override fun remove(key: ProfileTipsKey) { entries.remove(key) }
        override fun bindAccount(uid: String) { if (owner != uid) { entries.clear(); owner = uid } }
        override fun clear() { entries.clear(); owner = null }
    }

    private val disk = object : ProfileTipsStorage {
        val rows = mutableMapOf<Pair<String, Int>, ProfileTipsSnapshot>()
        override fun read(uid: String, groupId: Int) = rows[uid to groupId]
        override fun save(uid: String, snapshot: ProfileTipsSnapshot) { rows[uid to snapshot.groupId] = snapshot }
    }

    @Test fun networkResultIsSavedInSqliteAndMemory() = runTest {
        val repo = repository { _, _ -> tips }
        val result = repo.load(7)
        assertEquals(tips, result.tips)
        assertFalse(result.fromPhone)
        assertEquals(tips, disk.rows["leader" to 7]!!.tips)
        assertNotNull(memory.entries[ProfileTipsKey("leader", 7, 90)])
    }

    @Test fun freshMemoryEntrySkipsTheNetworkUntilTenMinutesPass() = runTest {
        var requests = 0
        val repo = repository { _, _ -> requests++; tips }
        repo.load(7)
        repo.load(7)
        assertEquals(1, requests)
        assertTrue("HIT group=7" in logs)

        now += PROFILE_TIPS_TTL_MS
        repo.load(7)
        assertEquals(2, requests)
        assertTrue("EXPIRED group=7" in logs)
    }

    @Test fun forceReloadIgnoresTheMemoryEntry() = runTest {
        var requests = 0
        val repo = repository { _, _ -> requests++; tips }
        repo.load(7)
        repo.load(7, force = true)
        assertEquals(2, requests)
    }

    @Test fun offlineFallsBackToTheSqliteCopyWithItsSyncTime() = runTest {
        disk.rows["leader" to 7] = ProfileTipsSnapshot(7, tips, syncedAt = 42L)
        val repo = repository { _, _ -> throw IOException("offline") }
        val result = repo.load(7)
        assertTrue(result.fromPhone)
        assertEquals(42L, result.syncedAt)
    }

    @Test fun offlineWithoutCopyGivesAClearError() = runTest {
        val repo = repository { _, _ -> throw IOException("offline") }
        val failure = runCatching { repo.load(7) }.exceptionOrNull()
        assertTrue(failure is ProfileTipsOffline)
    }

    @Test fun forbiddenKeepsTheSqliteCopyButReportsAccessDenied() = runTest {
        disk.rows["leader" to 7] = ProfileTipsSnapshot(7, tips, syncedAt = 42L)
        val repo = repository { _, _ -> throw ApiHttpException(403) }
        val failure = runCatching { repo.load(7, force = true) }.exceptionOrNull()
        assertTrue(failure is ProfileTipsAccessDenied)
        assertNotNull(disk.rows["leader" to 7])
    }

    @Test fun anotherAccountNeverSeesThePreviousAccountsMemory() = runTest {
        var requests = 0
        val repo = repository { _, _ -> requests++; tips }
        repo.load(7)
        uid = "other"
        repo.load(7)
        assertEquals(2, requests)
        assertNull(memory.entries[ProfileTipsKey("leader", 7, 90)])
    }

    @Test fun loadAllKeepsLoadingWhenOneGroupFails() = runTest {
        val repo = repository { id, _ -> if (id == 2) throw ApiHttpException(403) else tips }
        val results = repo.loadAll(listOf(1, 2, 3))
        assertTrue(results.getValue(1).isSuccess)
        assertTrue(results.getValue(2).exceptionOrNull() is ProfileTipsAccessDenied)
        assertTrue(results.getValue(3).isSuccess)
        assertEquals(1, results.getValue(3).getOrThrow().total)
    }

    private fun TestScope.repository(fetch: suspend (Int, Int) -> ProfileTips): ProfileTipsRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ProfileTipsRepository(
            storage = disk, fetch = fetch, currentUid = { uid }, memory = memory,
            clock = { now }, io = dispatcher, cpu = dispatcher, log = { logs += it },
        )
    }
}

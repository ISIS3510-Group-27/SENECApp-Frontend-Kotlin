package com.senecapp.data

import android.content.Context
import android.database.SQLException
import android.util.Log
import android.util.LruCache
import com.senecapp.BuildConfig
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException


internal data class ProfileTipsKey(val uid: String, val groupId: Int, val days: Int)


internal data class ProfileTipsEntry(val snapshot: ProfileTipsSnapshot, val cachedAt: Long)

internal interface ProfileTipsMemory {
    fun get(key: ProfileTipsKey): ProfileTipsEntry?
    fun put(key: ProfileTipsKey, entry: ProfileTipsEntry)
    fun remove(key: ProfileTipsKey)
    fun bindAccount(uid: String)
    fun clear()
}

internal const val PROFILE_TIPS_TTL_MS = 10 * 60 * 1000L


private const val PROFILE_TIPS_MAX_ROWS = 128

internal class ProfileTipsLruCache(
    private val log: (String) -> Unit = { if (BuildConfig.DEBUG) Log.d("ProfileTipsCache", it) },
) : ProfileTipsMemory {

    private val entries = object : LruCache<ProfileTipsKey, ProfileTipsEntry>(PROFILE_TIPS_MAX_ROWS) {
        override fun sizeOf(key: ProfileTipsKey, value: ProfileTipsEntry): Int =
            1 + value.snapshot.tips.fields.size

        override fun entryRemoved(
            evicted: Boolean, key: ProfileTipsKey, oldValue: ProfileTipsEntry, newValue: ProfileTipsEntry?,
        ) {
            if (evicted) log("EVICTION group=${key.groupId}")
        }
    }

    @Volatile private var owner: String? = null

    override fun get(key: ProfileTipsKey): ProfileTipsEntry? = entries.get(key)
    override fun put(key: ProfileTipsKey, entry: ProfileTipsEntry) { entries.put(key, entry) }
    override fun remove(key: ProfileTipsKey) { entries.remove(key) }

    @Synchronized override fun bindAccount(uid: String) {
        if (owner != uid) {
            entries.evictAll()
            owner = uid
        }
    }

    @Synchronized override fun clear() {
        entries.evictAll()
        owner = null
    }

    companion object {
        val shared: ProfileTipsLruCache by lazy { ProfileTipsLruCache() }
    }
}


internal class ProfileTipsOffline(cause: Throwable? = null) :
    IOException("No connection and no saved tips for this group yet.", cause)


internal class ProfileTipsAccessDenied : IllegalStateException("Only group admins can see these tips.")

internal class ProfileTipsRepository(
    private val storage: ProfileTipsStorage,
    private val fetch: suspend (groupId: Int, days: Int) -> ProfileTips,
    private val currentUid: () -> String?,
    private val memory: ProfileTipsMemory = ProfileTipsLruCache.shared,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = { if (BuildConfig.DEBUG) Log.d("ProfileTipsCache", it) },
) {
    constructor(context: Context) : this(
        storage = ProfileTipsStore(RSODatabase.get(context.applicationContext)),
        fetch = remoteProfileTips(backendClient()),
        currentUid = FirebaseIdTokenProvider::userId,
    )


    suspend fun load(groupId: Int, days: Int = 90, force: Boolean = false): ProfileTipsSnapshot = withContext(io) {
        val uid = currentUid() ?: throw SessionRequiredException()
        memory.bindAccount(uid)
        val key = ProfileTipsKey(uid, groupId, days)

        if (!force) fresh(key)?.let { return@withContext it }

        try {
            val tips = fetch(groupId, days)
            if (currentUid() != uid) throw SessionChangedException()
            val snapshot = ProfileTipsSnapshot(groupId, tips, syncedAt = clock())
            saveLocal(uid, snapshot)
            memory.put(key, ProfileTipsEntry(snapshot, snapshot.syncedAt))
            snapshot
        } catch (failure: IOException) {
            // No network (or timeout): show the last copy saved on the phone, with its sync time.
            readLocal(uid, groupId) ?: throw ProfileTipsOffline(failure)
        } catch (failure: ApiHttpException) {
            if (failure.status != 403) throw failure
            // Not an admin anymore: forget the memory entry but keep SQLite; the user may become admin again.
            memory.remove(key)
            throw ProfileTipsAccessDenied()
        }
    }

    suspend fun loadAll(groupIds: List<Int>, days: Int = 90): Map<Int, Result<ProfileTipsSummary>> = withContext(io) {
        val loaded = supervisorScope {
            groupIds.distinct()
                .map { id -> id to async { load(id, days) } }
                .map { (id, request) -> id to awaitResult { request.await() } }
        }

        withContext(cpu) {
            loaded.associate { (id, result) -> id to result.map(::summarizeProfileTips) }
        }
    }

    suspend fun summarize(snapshot: ProfileTipsSnapshot): ProfileTipsSummary =
        withContext(cpu) { summarizeProfileTips(snapshot) }

    fun clearMemory() = memory.clear()

    private fun fresh(key: ProfileTipsKey): ProfileTipsSnapshot? {
        val entry = memory.get(key)
        if (entry == null) { log("MISS group=${key.groupId}"); return null }
        if (clock() - entry.cachedAt !in 0 until PROFILE_TIPS_TTL_MS) {
            memory.remove(key)
            log("EXPIRED group=${key.groupId}")
            return null
        }
        log("HIT group=${key.groupId}")
        return entry.snapshot
    }

    private fun saveLocal(uid: String, snapshot: ProfileTipsSnapshot) {
        try {
            storage.save(uid, snapshot)
        } catch (_: SQLException) {

        }
    }

    private fun readLocal(uid: String, groupId: Int): ProfileTipsSnapshot? = try {
        storage.read(uid, groupId)?.copy(fromPhone = true)
    } catch (_: SQLException) {
        null
    }


    private suspend fun <T> awaitResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}

private fun remoteProfileTips(api: AuthenticatedApiClient): suspend (Int, Int) -> ProfileTips = { groupId, days ->
    parseProfileTips(JSONObject(api.request("/groups/$groupId/insights/profile?days=$days")))
}

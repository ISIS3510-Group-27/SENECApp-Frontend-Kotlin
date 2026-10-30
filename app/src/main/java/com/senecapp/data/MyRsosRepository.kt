package com.senecapp.data

import android.content.Context
import android.database.SQLException
import android.os.SystemClock
import android.util.Log
import com.senecapp.BuildConfig
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import org.json.JSONArray
import java.io.IOException


data class MyRsos(
    val mine: List<Rso>,
    val saved: List<Rso>,

    val fromCache: Boolean = false,
    val cachedAt: Long? = null,
)

class MyRsosRepository(context: Context) {
    private val api = backendClient()
    private val applicationContext = context.applicationContext
    private val cache = MyRsosCache(applicationContext)
    private val local = RsosLocalStore(RSODatabase.get(applicationContext))
    // The rows live in SQLite; this preference only remembers when each user last synced.
    private val syncTimes = applicationContext.getSharedPreferences("my_rsos_sync", Context.MODE_PRIVATE)

    suspend fun load(): MyRsos = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: throw SessionRequiredException()
        try {
            val started = SystemClock.elapsedRealtime()
            // Both requests run at the same time on IO. If one fails, coroutineScope cancels the other
            // and rethrows the failure, so the offline fallback below still sees the IOException.
            val (mine, saved) = coroutineScope {
                val mineRequest = async { JSONArray(api.request("/me/groups")) }
                val savedRequest = async { JSONArray(api.request("/me/saved-groups")) }
                mineRequest.await() to savedRequest.await()
            }
            if (BuildConfig.DEBUG) Log.d("MyRsosLoad", "parallel load took ${SystemClock.elapsedRealtime() - started} ms")
            val result = MyRsos(mine = parseList(mine), saved = parseList(saved))
            store(uid, result)
            result
        } catch (failure: IOException) {
            readLocal(uid) ?: readLegacy(uid) ?: throw failure
        }
    }
    fun clearCache() = cache.clear()

    fun online(): Boolean = isOnline(applicationContext)

    private fun store(uid: String, result: MyRsos) {
        try {
            local.replace(uid, result.mine, result.saved)
            syncTimes.edit().putLong(uid, System.currentTimeMillis()).apply()
        } catch (_: SQLException) {
            // The fresh list is still shown; only the offline copy is not updated.
        }
    }

    private fun readLocal(uid: String): MyRsos? {
        val syncedAt = syncTimes.getLong(uid, 0L).takeIf { it > 0L } ?: return null
        return try {
            val (mine, saved) = local.read(uid)
            MyRsos(mine = mine, saved = saved, fromCache = true, cachedAt = syncedAt)
        } catch (_: SQLException) {
            null
        }
    }

    // Sprint 2 SharedPreferences copy, kept as a fallback until the SQLite version is verified on devices.
    private fun readLegacy(uid: String): MyRsos? = cache.read(uid)?.let {
        MyRsos(mine = it.mine, saved = it.saved, fromCache = true, cachedAt = it.updatedAt)
    }
}

package com.senecapp.data

import android.content.Context
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun load(): MyRsos = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: throw SessionRequiredException()
        try {
            val mine = JSONArray(api.request("/me/groups"))
            val saved = JSONArray(api.request("/me/saved-groups"))
            cache.save(uid, mine.toString(), saved.toString())
            MyRsos(mine = parseList(mine), saved = parseList(saved))
        } catch (failure: IOException) {

            val cached = cache.read(uid) ?: throw failure
            MyRsos(mine = cached.mine, saved = cached.saved, fromCache = true, cachedAt = cached.updatedAt)
        }
    }

    fun clearCache() = cache.clear()

    fun online(): Boolean = isOnline(applicationContext)
}
package com.senecapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray


internal class MyRsosCache(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("my_rsos_cache", Context.MODE_PRIVATE)

    fun save(uid: String, mine: String, saved: String) {
        preferences.edit()
            .putString("$KEY_MINE$uid", mine)
            .putString("$KEY_SAVED$uid", saved)
            .putLong("$KEY_UPDATED_AT$uid", System.currentTimeMillis())
            .apply()
    }

    fun read(uid: String): CachedRsos? {
        val mine = preferences.getString("$KEY_MINE$uid", null) ?: return null
        val saved = preferences.getString("$KEY_SAVED$uid", null) ?: return null
        val updatedAt = preferences.getLong("$KEY_UPDATED_AT$uid", 0L).takeIf { it > 0L } ?: return null
        return runCatching {
            CachedRsos(parseList(JSONArray(mine)), parseList(JSONArray(saved)), updatedAt)
        }.getOrNull()
    }
    fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val KEY_MINE = "mine_"
        const val KEY_SAVED = "saved_"
        const val KEY_UPDATED_AT = "updated_at_"
    }
}

internal data class CachedRsos(val mine: List<Rso>, val saved: List<Rso>, val updatedAt: Long)

internal fun parseList(array: JSONArray): List<Rso> =
    List(array.length()) { parseRso(array.getJSONObject(it)) }

internal fun isOnline(context: Context): Boolean {
    val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val capabilities = runCatching {
        manager.getNetworkCapabilities(manager.activeNetwork)
    }.getOrNull() ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
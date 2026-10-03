package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CampusEvent(val id: Int, val title: String, val group: String, val startsAt: String,
    val building: String?, val description: String?, val cancelled: Boolean)

internal class CampusEventsRepository {
    private val api = backendClient()

    suspend fun load(mine: Boolean): Pair<List<CampusEvent>, Int> = withContext(Dispatchers.IO) {
        val root = JSONObject(api.request("/events?mine=$mine&limit=100"))
        val items = root.getJSONArray("items")
        List(items.length()) { parse(items.getJSONObject(it)) } to root.getInt("total")
    }

    suspend fun detail(id: Int): CampusEvent = withContext(Dispatchers.IO) {
        parse(JSONObject(api.request("/events/$id?entry_point=events")))
    }

    private fun parse(item: JSONObject) = CampusEvent(item.getInt("id"), item.getString("title"),
        item.getJSONObject("group").getString("name"), item.getString("starts_at"),
        item.optJSONObject("building")?.getString("name"),
        item.optString("description").takeUnless { item.isNull("description") || it.isBlank() },
        item.getBoolean("is_cancelled"))
}

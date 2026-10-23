package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CampusEvent(val id: Int, val title: String, val group: String, val startsAt: String,
    val building: String?, val description: String?, val cancelled: Boolean,
    val endsAt: String = startsAt, val groupId: Int = 0, val color: String? = null,
    val checkedIn: Boolean = false, val attendeeCount: Int = 0, val capacity: Int? = null,
    val locationDetail: String? = null)

internal fun parseCampusEvent(item: JSONObject, groupName: String = "", groupColor: String? = null) = CampusEvent(
    item.getInt("id"), item.getString("title"), item.optJSONObject("group")?.optString("name") ?: groupName,
    item.getString("starts_at"), item.optJSONObject("building")?.textOrNull("name"), item.textOrNull("description"),
    item.optBoolean("is_cancelled"), item.optString("ends_at", item.getString("starts_at")),
    item.optInt("group_id"), item.optJSONObject("group")?.textOrNull("color") ?: groupColor,
    item.optBoolean("checked_in"), item.optInt("attendee_count"),
    item.optInt("capacity").takeUnless { item.isNull("capacity") }, item.textOrNull("location_detail"))

internal class CampusEventsRepository {
    private val api = backendClient()

    suspend fun load(mine: Boolean): Pair<List<CampusEvent>, Int> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CampusEvent>()
        var total: Int
        do {
            val root = JSONObject(api.request("/events?mine=$mine&limit=100&offset=${events.size}"))
            val page = root.getJSONArray("items").readItems(::parseCampusEvent)
            events += page
            total = root.getInt("total")
        } while (page.isNotEmpty() && events.size < total)
        events to total
    }

    suspend fun detail(id: Int, source: String = "events", requestId: String? = null): CampusEvent = withContext(Dispatchers.IO) {
        val rec = requestId?.let { "&rec_request_id=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        parseCampusEvent(JSONObject(api.request("/events/$id?entry_point=$source$rec")))
    }

    suspend fun attendedSince(start: String): Int {
        var offset = 0
        var count = 0
        do {
            val root = JSONObject(api.request("/events?mine=true&starts_after=$start&limit=100&offset=$offset"))
            val page = root.getJSONArray("items").readItems(::parseCampusEvent)
            count += page.count { it.checkedIn }
            offset += page.size
        } while (page.isNotEmpty() && offset < root.getInt("total"))
        return count
    }
}

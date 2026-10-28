package com.senecapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal data class CachedEvents(val items: List<CampusEvent>, val total: Int, val updatedAt: Long)

internal interface EventsCache {
    fun read(uid: String, mine: Boolean): CachedEvents?
    fun save(uid: String, mine: Boolean, snapshot: CachedEvents): Boolean
}

// Called on the repository's IO dispatcher. Each key holds one complete, user-owned snapshot.
internal class CachedEventsStore(private val preferences: SharedPreferences) : EventsCache {
    constructor(context: Context) : this(context.applicationContext
        .getSharedPreferences("campus_events_cache", Context.MODE_PRIVATE))

    override fun read(uid: String, mine: Boolean): CachedEvents? {
        if (uid.isBlank()) return null
        return try {
            val root = JSONObject(preferences.getString(key(uid, mine), null) ?: return null)
            if (root.getInt("version") != 1 || root.getString("uid") != uid || root.getBoolean("mine") != mine) return null
            val array = root.getJSONArray("items")
            val items = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                CampusEvent(id = item.getInt("id"), title = item.getString("title"), group = item.getString("group"),
                    startsAt = item.getString("startsAt"), building = item.nullableText("building"),
                    description = item.nullableText("description"), cancelled = item.getBoolean("cancelled"),
                    endsAt = item.getString("endsAt"), groupId = item.getInt("groupId"), color = item.nullableText("color"),
                    checkedIn = item.getBoolean("checkedIn"), attendeeCount = item.getInt("attendeeCount"),
                    capacity = if (item.isNull("capacity")) null else item.getInt("capacity"),
                    locationDetail = item.nullableText("locationDetail"))
            }
            val total = root.getInt("total")
            val updatedAt = root.getLong("updatedAt")
            if (total != items.size || updatedAt <= 0L) null else CachedEvents(items, total, updatedAt)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { null }
    }

    override fun save(uid: String, mine: Boolean, snapshot: CachedEvents): Boolean {
        if (uid.isBlank() || snapshot.total != snapshot.items.size || snapshot.updatedAt <= 0L) return false
        return try {
            val items = JSONArray()
            snapshot.items.forEach { event ->
                items.put(JSONObject().put("id", event.id).put("title", event.title).put("group", event.group)
                    .put("startsAt", event.startsAt).put("building", event.building ?: JSONObject.NULL)
                    .put("description", event.description ?: JSONObject.NULL).put("cancelled", event.cancelled)
                    .put("endsAt", event.endsAt).put("groupId", event.groupId).put("color", event.color ?: JSONObject.NULL)
                    .put("checkedIn", event.checkedIn).put("attendeeCount", event.attendeeCount)
                    .put("capacity", event.capacity ?: JSONObject.NULL).put("locationDetail", event.locationDetail ?: JSONObject.NULL))
            }
            val root = JSONObject().put("version", 1).put("uid", uid).put("mine", mine)
                .put("updatedAt", snapshot.updatedAt).put("total", snapshot.total).put("items", items)
            preferences.edit().putString(key(uid, mine), root.toString()).commit()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { false }
    }

    private fun key(uid: String, mine: Boolean) = "events.$uid.$mine"
    private fun JSONObject.nullableText(name: String): String? = if (isNull(name)) null else getString(name)
}

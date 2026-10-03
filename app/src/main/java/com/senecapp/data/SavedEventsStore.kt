package com.senecapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local snapshots only: never stores GPS, tokens, or a recommendation request ID. */
internal class SavedEventsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("saved_events", Context.MODE_PRIVATE)

    suspend fun load(): List<FreeNowEvent> = withContext(Dispatchers.IO) {
        val items = JSONArray(preferences.getString("events", "[]"))
        List(items.length()) { index ->
            val event = items.getJSONObject(index)
            FreeNowEvent(id = event.getInt("id"), title = event.getString("title"),
                groupName = event.getString("group"), startsAt = event.getString("starts_at"),
                description = event.optString("description").takeUnless { event.isNull("description") },
                buildingName = event.optString("building").takeUnless { event.isNull("building") },
                walkingMinutes = null, reasons = emptyList())
        }.distinctBy { it.id }
    }

    suspend fun save(events: List<FreeNowEvent>) = withContext(Dispatchers.IO) {
        val items = JSONArray()
        events.distinctBy { it.id }.forEach { event ->
            items.put(JSONObject().put("id", event.id).put("title", event.title)
                .put("group", event.groupName).put("starts_at", event.startsAt)
                .put("description", event.description ?: JSONObject.NULL)
                .put("building", event.buildingName ?: JSONObject.NULL))
        }
        check(preferences.edit().putString("events", items.toString()).commit()) {
            "Could not save events on this device."
        }
    }
}

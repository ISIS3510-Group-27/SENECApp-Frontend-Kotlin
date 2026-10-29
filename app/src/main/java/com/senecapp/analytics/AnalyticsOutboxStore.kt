package com.senecapp.analytics

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class QueuedAnalyticsEvent(val event: AnalyticsEvent, val quarantined: Boolean = false)

// All access is serialized by AnalyticsPipeline on its IO dispatcher. Separate from the Events caches.
internal class AnalyticsOutboxStore(private val preferences: () -> SharedPreferences) {
    fun read(): List<QueuedAnalyticsEvent> {
        val array = try { JSONArray(preferences().getString("outbox", "[]")) } catch (_: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            try {
                val row = array.getJSONObject(index)
                QueuedAnalyticsEvent(AnalyticsEvent.read(row.getJSONObject("event")), row.getBoolean("quarantined"))
            } catch (_: Exception) { null }
        }.distinctBy { it.event.id }.takeLast(MAX_EVENTS)
    }

    fun write(events: List<QueuedAnalyticsEvent>): Boolean {
        val array = JSONArray()
        events.distinctBy { it.event.id }.takeLast(MAX_EVENTS).forEach {
            array.put(JSONObject().put("event", it.event.json()).put("quarantined", it.quarantined))
        }
        return preferences().edit().putString("outbox", array.toString()).commit()
    }

    companion object { const val MAX_EVENTS = 500 }
}

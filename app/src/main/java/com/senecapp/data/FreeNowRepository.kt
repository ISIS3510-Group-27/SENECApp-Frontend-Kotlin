package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class FreeNowRepository {
    private val api = backendClient()

    suspend fun suggestions(at: String? = null, coordinates: EventCoordinates? = null): FreeNowSuggestion = withContext(Dispatchers.IO) {
        val demoTime = at?.let { "&at=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val gps = coordinates?.let { "&latitude=${it.latitude}&longitude=${it.longitude}" } ?: ""
        val root = JSONObject(api.request("/recommendations/events/free-now?limit=3$demoTime$gps"))
        val block = root.optJSONObject("free_block")
        val location = root.getJSONObject("location")
        val items = root.getJSONArray("items")
        FreeNowSuggestion(requestId = root.getString("request_id"),
            freeStartsAt = block?.getString("starts_at"), freeEndsAt = block?.getString("ends_at"),
            freeMinutes = block?.getInt("minutes"), scheduleKnown = root.getBoolean("schedule_known"),
            locationName = location.optJSONObject("building")?.getString("name"),
            locationSource = location.optString("source", "none"),
            events = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                val event = item.getJSONObject("event")
                val reasons = item.getJSONArray("reasons")
                FreeNowEvent(id = event.getInt("id"), title = event.getString("title"),
                    groupName = event.getJSONObject("group").getString("name"),
                    description = event.optString("description").takeUnless { event.isNull("description") || it.isBlank() },
                    startsAt = event.getString("starts_at"), buildingName = event.optJSONObject("building")?.getString("name"),
                    walkingMinutes = item.optInt("walking_minutes").takeUnless { item.isNull("walking_minutes") },
                    reasons = List(reasons.length()) { reasons.getString(it) })
            }, message = root.optString("message").takeUnless { root.isNull("message") || it.isBlank() })
    }

    suspend fun locationConsent(): Boolean = JSONObject(api.request("/me")).getBoolean("location_opt_in")

    suspend fun setLocationConsent(enabled: Boolean): Boolean {
        val body = JSONObject().put("location_opt_in", enabled).toString()
        return JSONObject(api.request("/me", "PATCH", body)).getBoolean("location_opt_in")
    }

    suspend fun openEvent(eventId: Int, requestId: String) {
        api.request("/events/$eventId?entry_point=free_now&rec_request_id=$requestId")
    }
}

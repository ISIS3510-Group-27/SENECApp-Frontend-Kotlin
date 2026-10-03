package com.senecapp.data

import android.os.Build
import com.senecapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class FreeNowRepository {
    private val sessionId = UUID.randomUUID().toString()

    suspend fun suggestions(at: String? = null, coordinates: EventCoordinates? = null): FreeNowSuggestion = withContext(Dispatchers.IO) {
        val demoTime = at?.let { "&at=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val gps = coordinates?.let { "&latitude=${it.latitude}&longitude=${it.longitude}" } ?: ""
        val connection = get("/recommendations/events/free-now?limit=3$demoTime$gps")
        try {
            checkResponse(connection)
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val block = root.optJSONObject("free_block")
            val location = root.getJSONObject("location")
            val items = root.getJSONArray("items")
            FreeNowSuggestion(
                requestId = root.getString("request_id"),
                freeStartsAt = block?.getString("starts_at"),
                freeEndsAt = block?.getString("ends_at"),
                freeMinutes = block?.getInt("minutes"),
                scheduleKnown = root.getBoolean("schedule_known"),
                locationName = location.optJSONObject("building")?.getString("name"),
                locationSource = location.optString("source", "none"),
                events = List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    val event = item.getJSONObject("event")
                    val reasons = item.getJSONArray("reasons")
                    FreeNowEvent(
                        id = event.getInt("id"),
                        title = event.getString("title"),
                        groupName = event.getJSONObject("group").getString("name"),
                        description = event.optString("description").takeUnless { event.isNull("description") || it.isBlank() },
                        startsAt = event.getString("starts_at"),
                        buildingName = event.optJSONObject("building")?.getString("name"),
                        walkingMinutes = item.optInt("walking_minutes").takeUnless { item.isNull("walking_minutes") },
                        reasons = List(reasons.length()) { reasons.getString(it) },
                    )
                },
                message = root.optString("message").takeUnless { root.isNull("message") || it.isBlank() },
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun locationConsent(): Boolean = withContext(Dispatchers.IO) {
        val connection = get("/me")
        try {
            checkResponse(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getBoolean("location_opt_in")
        } finally { connection.disconnect() }
    }

    suspend fun setLocationConsent(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val connection = get("/me")
        try {
            connection.requestMethod = "PATCH"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("location_opt_in", enabled).toString().toByteArray(Charsets.UTF_8))
            }
            checkResponse(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getBoolean("location_opt_in")
        } finally { connection.disconnect() }
    }

    suspend fun openEvent(eventId: Int, requestId: String) = withContext(Dispatchers.IO) {
        val connection = get("/events/$eventId?entry_point=free_now&rec_request_id=$requestId")
        try {
            checkResponse(connection)
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun get(path: String): HttpURLConnection {
        if (BuildConfig.API_BASE_URL.isBlank()) error("API is not configured for this build")
        return (URL("${BuildConfig.API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Authorization", "Bearer ${BuildConfig.DEV_TOKEN}")
            setRequestProperty("X-App", "kotlin")
            setRequestProperty("X-Session-Id", sessionId)
            setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            setRequestProperty("X-Platform", "android")
            setRequestProperty("X-Device-Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            setRequestProperty("X-OS-Version", Build.VERSION.RELEASE)
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        when (connection.responseCode) {
            HttpURLConnection.HTTP_OK -> Unit
            HttpURLConnection.HTTP_UNAUTHORIZED -> error("The demo token was rejected. Check AUTH_PROVIDER=dev.")
            else -> error("Server returned HTTP ${connection.responseCode}.")
        }
    }
}

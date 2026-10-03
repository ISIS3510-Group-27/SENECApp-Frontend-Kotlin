package com.senecapp.data

import android.os.Build
import com.senecapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CampusEvent(val id: Int, val title: String, val group: String, val startsAt: String,
    val building: String?, val description: String?, val cancelled: Boolean)

internal class CampusEventsRepository {
    private val sessionId = UUID.randomUUID().toString()

    suspend fun load(mine: Boolean): Pair<List<CampusEvent>, Int> = withContext(Dispatchers.IO) {
        val connection = connect("/events?mine=$mine&limit=100")
        try {
            checkResponse(connection)
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val items = root.getJSONArray("items")
            List(items.length()) { parse(items.getJSONObject(it)) } to root.getInt("total")
        } finally { connection.disconnect() }
    }

    suspend fun detail(id: Int): CampusEvent = withContext(Dispatchers.IO) {
        val connection = connect("/events/$id?entry_point=events")
        try {
            checkResponse(connection)
            parse(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
        } finally { connection.disconnect() }
    }

    private fun parse(item: JSONObject) = CampusEvent(item.getInt("id"), item.getString("title"),
        item.getJSONObject("group").getString("name"), item.getString("starts_at"),
        item.optJSONObject("building")?.getString("name"),
        item.optString("description").takeUnless { item.isNull("description") || it.isBlank() },
        item.getBoolean("is_cancelled"))

    private fun connect(path: String): HttpURLConnection {
        check(BuildConfig.API_BASE_URL.isNotBlank()) { "API is not configured for this build" }
        return (URL("${BuildConfig.API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
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
        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "Could not load events (HTTP ${connection.responseCode})."
        }
    }
}

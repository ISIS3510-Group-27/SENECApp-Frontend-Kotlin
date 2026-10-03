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

class OrganizationsRepository {
    private val sessionId = UUID.randomUUID().toString()

    suspend fun search(query: String, categorySlug: String?, upcomingOnly: Boolean = false): List<Organization> = withContext(Dispatchers.IO) {
        if (BuildConfig.API_BASE_URL.isBlank()) error("API is not configured for this build")

        val parameters = mutableListOf("limit=100")
        if (query.isNotBlank()) parameters += "q=${encode(query.trim())}"
        if (categorySlug != null) parameters += "category=${encode(categorySlug)}"
        if (upcomingOnly) parameters += "has_upcoming_events=true"
        val url = URL("${BuildConfig.API_BASE_URL}/groups?${parameters.joinToString("&")}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
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

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val items = root.getJSONArray("items")
                    List(items.length()) { index ->
                        val item = items.getJSONObject(index)
                        val category = item.getJSONObject("category")
                        Organization(
                            id = item.getInt("id"),
                            name = item.getString("name"),
                            category = category.getString("label"),
                            categorySlug = category.getString("slug"),
                            members = item.getInt("member_count"),
                            color = item.optString("color").takeIf { it.startsWith("#") },
                            hasUpcomingEvent = !item.isNull("next_event"),
                        )
                    }
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> error("The demo token was rejected. Check AUTH_PROVIDER=dev.")
                else -> error("Server returned HTTP ${connection.responseCode}.")
            }
        } finally {
            connection.disconnect()
        }
    }

    // A real detail request records group_viewed, linked to the search in BQ12.
    suspend fun detail(id: Int, fromSearch: Boolean): String = withContext(Dispatchers.IO) {
        val entryPoint = if (fromSearch) "search" else "explore"
        val connection = URL("${BuildConfig.API_BASE_URL}/groups/$id?entry_point=$entryPoint")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.DEV_TOKEN}")
            connection.setRequestProperty("X-App", "kotlin")
            connection.setRequestProperty("X-Session-Id", sessionId)
            connection.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            connection.setRequestProperty("X-Platform", "android")
            connection.setRequestProperty("X-Device-Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            connection.setRequestProperty("X-OS-Version", Build.VERSION.RELEASE)
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Could not load organization details." }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("description")
        } finally { connection.disconnect() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

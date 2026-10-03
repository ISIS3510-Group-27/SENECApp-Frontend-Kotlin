package com.senecapp.data

import android.os.Build
import com.senecapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroupRecommendationsRepository {
    private val sessionId = UUID.randomUUID().toString()

    suspend fun load(): GroupRecommendations = withContext(Dispatchers.IO) {
        val connection = connect("/recommendations/groups?limit=3")
        try {
            checkResponse(connection)
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val items = root.getJSONArray("items")
            GroupRecommendations(
                requestId = root.getString("request_id"),
                items = List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    val group = item.getJSONObject("group")
                    val reasons = item.getJSONArray("reasons")
                    GroupRecommendation(
                        id = group.getInt("id"),
                        name = group.getString("name"),
                        category = group.getJSONObject("category").getString("label"),
                        description = group.getString("description"),
                        memberCount = group.getInt("member_count"),
                        reasons = List(reasons.length()) { reasons.getString(it) },
                        isMember = group.getBoolean("is_member"),
                    )
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun openGroup(groupId: Int, requestId: String) = withContext(Dispatchers.IO) {
        val connection = connect("/groups/$groupId?entry_point=recommendation&rec_request_id=$requestId")
        try {
            checkResponse(connection)
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun joinGroup(groupId: Int, requestId: String) = withContext(Dispatchers.IO) {
        val connection = connect("/groups/$groupId/join", "POST")
        try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val body = JSONObject()
                .put("entry_point", "recommendation")
                .put("rec_request_id", requestId)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checkResponse(connection)
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(path: String, method: String = "GET"): HttpURLConnection {
        if (BuildConfig.API_BASE_URL.isBlank()) error("API is not configured for this build")
        return (URL("${BuildConfig.API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
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
            HttpURLConnection.HTTP_CONFLICT -> error("You are already a member of this group.")
            HttpURLConnection.HTTP_UNAUTHORIZED -> error("The demo token was rejected. Check AUTH_PROVIDER=dev.")
            else -> error("Server returned HTTP ${connection.responseCode}.")
        }
    }
}

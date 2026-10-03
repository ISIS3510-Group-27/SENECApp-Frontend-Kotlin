package com.senecapp.data

import android.os.Build
import com.senecapp.BuildConfig
import com.senecapp.auth.FirebaseIdTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal fun backendClient() = AuthenticatedApiClient(FirebaseIdTokenProvider, HttpApiTransport())

private class HttpApiTransport : ApiTransport {
    private var sessionUser: String? = null
    private var sessionId = UUID.randomUUID().toString()

    @Synchronized private fun sessionFor(userId: String): String {
        if (userId != sessionUser) {
            sessionUser = userId
            sessionId = UUID.randomUUID().toString()
        }
        return sessionId
    }

    override suspend fun execute(path: String, method: String, body: String?, token: String, userId: String): ApiResponse =
        withContext(Dispatchers.IO) {
            check(BuildConfig.API_BASE_URL.isNotBlank()) { "API is not configured for this build." }
            val connection = URL("${BuildConfig.API_BASE_URL}$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-App", "kotlin")
                connection.setRequestProperty("X-Session-Id", sessionFor(userId))
                connection.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
                connection.setRequestProperty("X-Platform", "android")
                connection.setRequestProperty("X-Device-Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                connection.setRequestProperty("X-OS-Version", Build.VERSION.RELEASE)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                // Server error bodies may contain personal data. They are never logged or displayed.
                val result = if (status in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                ApiResponse(status, result)
            } finally { connection.disconnect() }
        }
}

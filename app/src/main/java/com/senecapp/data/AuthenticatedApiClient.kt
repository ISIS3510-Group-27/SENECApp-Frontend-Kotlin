package com.senecapp.data

import com.senecapp.auth.IdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException

internal data class ApiResponse(val status: Int, val body: String)
internal fun interface ApiTransport {
    suspend fun execute(path: String, method: String, body: String?, token: String, userId: String): ApiResponse
}

internal class ApiHttpException(val status: Int) : IllegalStateException(when (status) {
    403 -> "Access denied. Check your verified university account and the backend configuration."
    404 -> "This item is no longer available."
    409 -> "This action conflicts with your current membership."
    else -> "The backend returned HTTP $status. Please try again."
})

internal class AuthenticatedApiClient(private val tokens: IdTokenProvider, private val transport: ApiTransport) {
    suspend fun request(path: String, method: String = "GET", body: String? = null): String {
        val userId = tokens.userId() ?: throw SessionRequiredException()
        // A 401 gets one fresh token and one retry, including for /me.
        repeat(2) { attempt ->
            if (tokens.userId() != userId) throw SessionChangedException()
            val token = tokens.token(forceRefresh = attempt == 1)
            if (tokens.userId() != userId) throw SessionChangedException()
            val response = transport.execute(path, method, body, token, userId)
            if (tokens.userId() != userId) throw SessionChangedException()
            if (response.status == 401) {
                if (attempt == 1) {
                    tokens.invalidate(userId)
                    throw SessionRequiredException()
                }
            } else {
                if (response.status !in 200..299) throw ApiHttpException(response.status)
                return response.body
            }
        }
        throw SessionRequiredException()
    }
}

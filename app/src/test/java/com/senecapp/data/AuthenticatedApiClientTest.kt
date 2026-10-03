package com.senecapp.data

import com.senecapp.auth.IdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthenticatedApiClientTest {
    private class Tokens : IdTokenProvider {
        var uid: String? = "a"
        val refreshes = mutableListOf<Boolean>()
        var invalidated: String? = null
        var changeDuringToken = false
        override fun userId() = uid
        override suspend fun token(forceRefresh: Boolean): String {
            refreshes += forceRefresh
            if (changeDuringToken) uid = "b"
            return if (forceRefresh) "renewed" else "current"
        }
        override fun invalidate(expectedUserId: String) { invalidated = expectedUserId; uid = null }
    }

    @Test fun renewsOnceAfter401AndPreservesTheRequest() = runBlocking {
        val tokens = Tokens()
        val requests = mutableListOf<List<String?>>()
        val client = AuthenticatedApiClient(tokens) { path, method, body, token, uid ->
            requests += listOf(path, method, body, token, uid)
            ApiResponse(if (requests.size == 1) 401 else 200, "ok")
        }
        assertEquals("ok", client.request("/groups/1/join", "POST", "body"))
        assertEquals(listOf(false, true), tokens.refreshes)
        assertEquals(listOf("/groups/1/join", "POST", "body", "renewed", "a"), requests.last())
        assertNull(tokens.invalidated)
    }

    @Test fun repeated401EndsSessionWithoutAnotherRetry() = runBlocking {
        val tokens = Tokens()
        var requests = 0
        val client = AuthenticatedApiClient(tokens) { _, _, _, _, _ -> requests++; ApiResponse(401, "") }
        try { client.request("/me"); fail("Expected an ended session")
        } catch (_: SessionRequiredException) { }
        assertEquals(2, requests)
        assertEquals("a", tokens.invalidated)
    }

    @Test fun noAccountCannotMakeARequest() = runBlocking {
        val tokens = Tokens().apply { uid = null }
        var requests = 0
        val client = AuthenticatedApiClient(tokens) { _, _, _, _, _ -> requests++; ApiResponse(200, "") }
        try { client.request("/me"); fail("Expected a required session")
        } catch (_: SessionRequiredException) { }
        assertEquals(0, requests)
    }

    @Test fun changedAccountBeforeSendDoesNotSendTheOldRequest() = runBlocking {
        val tokens = Tokens().apply { changeDuringToken = true }
        var requests = 0
        val client = AuthenticatedApiClient(tokens) { _, _, _, _, _ -> requests++; ApiResponse(200, "") }
        try { client.request("/me"); fail("Expected an account change")
        } catch (_: SessionChangedException) { }
        assertEquals(0, requests)
    }

    @Test fun stale401DoesNotSignOutTheNewAccount() = runBlocking {
        val tokens = Tokens()
        val client = AuthenticatedApiClient(tokens) { _, _, _, _, _ -> tokens.uid = "b"; ApiResponse(401, "") }
        try { client.request("/me"); fail("Expected an account change")
        } catch (_: SessionChangedException) { }
        assertEquals("b", tokens.uid)
        assertNull(tokens.invalidated)
        assertEquals(listOf(false), tokens.refreshes)
    }

    @Test fun forbiddenIsNotRetried() = runBlocking {
        val tokens = Tokens()
        var requests = 0
        val client = AuthenticatedApiClient(tokens) { _, _, _, _, _ -> requests++; ApiResponse(403, "") }
        try { client.request("/me"); fail("Expected forbidden")
        } catch (failure: ApiHttpException) { assertEquals(403, failure.status) }
        assertEquals(1, requests)
        assertNull(tokens.invalidated)
    }
}

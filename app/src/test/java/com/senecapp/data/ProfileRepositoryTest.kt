package com.senecapp.data

import com.senecapp.auth.IdTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {
    private val body = """{"id":2,"email":"student@uniandes.edu.co","full_name":"Student",
        "program":"Engineering","semester":3,"interests":[{"id":4,"name":"Music"}],
        "location_opt_in":true,"notifications_opt_in":false}"""

    private fun client(transport: ApiTransport): AuthenticatedApiClient = AuthenticatedApiClient(object : IdTokenProvider {
        override fun userId() = "profile-test-user"
        override suspend fun token(forceRefresh: Boolean) = "test-token"
        override fun invalidate(expectedUserId: String) = Unit
    }, transport)

    @Test fun loadUsesInjectedDispatcherAndReturnsTheSameProfileToTheCallingContext() = runTest {
        val caller = currentCoroutineContext()[ContinuationInterceptor]
        val io = StandardTestDispatcher(testScheduler, name = "profile-io")
        var calls = 0
        val api = client { path, method, requestBody, _, _ ->
            calls++
            assertEquals("/me", path)
            assertEquals("GET", method)
            assertNull(requestBody)
            assertSame(io, currentCoroutineContext()[ContinuationInterceptor])
            ApiResponse(200, body)
        }
        val result = ProfileRepository(api, io).load()
        assertEquals(parseProfile(JSONObject(body)), result)
        assertEquals(1, calls)
        assertSame(caller, currentCoroutineContext()[ContinuationInterceptor])
    }

    @Test fun profileParsingFailsInsideInjectedIoScopeOnAWorkerThread() {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "profile-test-io") }
            .asCoroutineDispatcher().use { io ->
                runTest {
                    val callerThread = Thread.currentThread()
                    val requestThread = AtomicReference<Thread>()
                    val parsingCompletion = AtomicReference<Pair<Thread, Throwable?>>()
                    val api = client { _, _, _, _, _ ->
                        assertSame(io, currentCoroutineContext()[ContinuationInterceptor])
                        requestThread.set(Thread.currentThread())
                        currentCoroutineContext().job.invokeOnCompletion { failure ->
                            parsingCompletion.set(Thread.currentThread() to failure)
                        }
                        // Valid JSON, but missing the id required by parseProfile().
                        ApiResponse(200, """{"email":"student@uniandes.edu.co"}""")
                    }
                    try {
                        ProfileRepository(api, io).load()
                        fail("Expected the original profile parsing failure")
                    } catch (_: JSONException) { }
                    val (parsingThread, failure) = requireNotNull(parsingCompletion.get())
                    // If only the request were offloaded, its IO scope would complete successfully
                    // and the parsing failure would occur later on the caller instead.
                    assertTrue(failure is JSONException)
                    assertSame(requestThread.get(), parsingThread)
                    assertNotSame(callerThread, parsingThread)
                    assertSame(callerThread, Thread.currentThread())
                    assertTrue(currentCoroutineContext().isActive)
                }
            }
    }

    @Test fun networkAndHttpFailuresPropagateWithoutChangingTheirMeaning() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val networkFailure = IOException("Offline")
        try {
            ProfileRepository(client { _, _, _, _, _ -> throw networkFailure }, io).load()
            fail("Expected the network failure")
        } catch (failure: IOException) { assertEquals(networkFailure.message, failure.message) }

        var calls = 0
        try {
            ProfileRepository(client { _, _, _, _, _ -> calls++; ApiResponse(403, "") }, io).load()
            fail("Expected the HTTP failure")
        } catch (failure: ApiHttpException) { assertEquals(403, failure.status) }
        assertEquals(1, calls)
    }

    @Test fun transportCancellationIsNotConvertedIntoAnApplicationError() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        try {
            ProfileRepository(client { _, _, _, _, _ -> throw CancellationException("Cancelled") }, io).load()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancellingTheCallerCancelsItsSuspendedProfileLoadWithoutPublishingAResult() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        var started = false
        var cleanedUp = false
        var returned = false
        val api = client { _, _, _, _, _ ->
            started = true
            try { awaitCancellation() } finally { cleanedUp = true }
        }
        val request = launch { ProfileRepository(api, io).load(); returned = true }
        runCurrent()
        assertTrue(started)
        request.cancel()
        runCurrent()
        assertTrue(request.isCancelled)
        assertTrue(request.isCompleted)
        assertTrue(cleanedUp)
        assertFalse(returned)
    }

    @Test fun nullProfileFieldsStayEmpty() {
        val profile = parseProfile(JSONObject("""{"id":1,"email":"student@uniandes.edu.co","full_name":null,
            "program":null,"semester":null,"interests":[],"location_opt_in":false,"notifications_opt_in":false}"""))
        assertNull(profile.fullName)
        assertNull(profile.program)
        assertNull(profile.semester)
        assertTrue(profile.interests.isEmpty())
    }

    @Test fun parsesOnlyTheReturnedProfileData() {
        val profile = parseProfile(JSONObject("""{"id":2,"email":"student@uniandes.edu.co","full_name":"Student",
            "program":"Engineering","semester":3,"interests":[{"id":4,"name":"Music"}],
            "location_opt_in":true,"notifications_opt_in":false}"""))
        assertEquals("Student", profile.fullName)
        assertEquals(3, profile.semester)
        assertEquals(listOf("Music"), profile.interests)
        assertTrue(profile.locationOptIn)
        assertFalse(profile.notificationsOptIn)
    }
}

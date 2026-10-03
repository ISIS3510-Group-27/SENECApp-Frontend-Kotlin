package com.senecapp.ui

import androidx.lifecycle.ViewModelStore
import com.senecapp.auth.SessionAccount
import com.senecapp.auth.SessionAuth
import com.senecapp.auth.SessionRequiredException
import com.senecapp.data.ProfileSource
import com.senecapp.data.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Auth(var current: SessionAccount? = null) : SessionAuth {
        override val configured = true
        var listener: (() -> Unit)? = null
        var reloadError: Exception? = null
        var loginError: Exception? = null
        var refreshed = 0
        var resets = 0
        override fun account() = current
        override fun observe(onChange: () -> Unit): () -> Unit { listener = onChange; return { listener = null } }
        override suspend fun signIn(email: String, password: String) {
            loginError?.let { throw it }
            current = SessionAccount("a", email, true); listener?.invoke()
        }
        override suspend fun reload() { reloadError?.let { throw it } }
        override suspend fun refreshToken() { refreshed++ }
        override suspend fun resetPassword(email: String) { resets++ }
        override fun signOut() { current = null; listener?.invoke() }
        fun change(account: SessionAccount?) { current = account; listener?.invoke() }
    }

    private fun profile(email: String = "a@uniandes.edu.co") = UserProfile(1, email, null, null, null, emptyList(), false, false)
    private fun model(auth: Auth, source: ProfileSource = ProfileSource { profile() }): SessionViewModel =
        SessionViewModel(auth, source).also { store.put("session", it) }

    @Test fun noSessionStartsSignedOut() = runTest(dispatcher) {
        var loads = 0
        val vm = model(Auth(), ProfileSource { loads++; profile() })
        advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNull(vm.state.profile)
        assertEquals(0, loads)
    }

    @Test fun unverifiedAccountCannotLoadProtectedData() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", false))
        var loads = 0
        val vm = model(auth, ProfileSource { loads++; profile() })
        advanceUntilIdle(); vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.VERIFICATION, vm.state.phase)
        assertEquals(0, auth.refreshed)
        assertEquals(0, loads)
    }

    @Test fun restoredVerifiedAccountRefreshesTokenBeforeProfile() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", true))
        val vm = model(auth, ProfileSource { assertEquals(1, auth.refreshed); profile() })
        assertEquals(SessionPhase.CHECKING, vm.state.phase)
        advanceUntilIdle()
        assertEquals(SessionPhase.PROFILE_LOADING, vm.state.phase)
        assertNull(vm.state.profile)
        vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_IN, vm.state.phase)
        vm.signOut()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNull(vm.state.uid)
        assertNull(vm.state.profile)
    }

    @Test fun invalidRestoredSessionIsRemoved() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", true)).apply { reloadError = SessionRequiredException() }
        val vm = model(auth)
        advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNull(auth.current)
        assertNull(vm.state.profile)
    }

    @Test fun verificationRefreshesTokenBeforeUnlockingProfile() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", false))
        val vm = model(auth)
        advanceUntilIdle()
        auth.current = auth.current!!.copy(verified = true)
        vm.checkSession(); advanceUntilIdle()
        assertEquals(1, auth.refreshed)
        assertEquals(SessionPhase.PROFILE_LOADING, vm.state.phase)
        assertNull(vm.state.profile)
        vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_IN, vm.state.phase)
    }

    @Test fun failedSessionCheckCannotShowProtectedData() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", true)).apply { reloadError = IOException() }
        val vm = model(auth)
        advanceUntilIdle()
        assertEquals(SessionPhase.SESSION_ERROR, vm.state.phase)
        assertNull(vm.state.profile)
    }

    @Test fun nonUniversityAccountIsRejectedEvenIfVerified() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@example.com", true))
        val vm = model(auth)
        advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNull(auth.current)
        assertEquals(0, auth.refreshed)
    }

    @Test fun failedLoginDoesNotLoadProfile() = runTest(dispatcher) {
        val auth = Auth().apply { loginError = IllegalArgumentException("Bad credentials") }
        val vm = model(auth)
        vm.signIn("a@uniandes.edu.co", "wrong"); advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNotNull(vm.state.error)
        assertNull(vm.state.profile)
    }

    @Test fun connectionFailureBlocksInitialProfileAndCanBeRetried() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", true))
        var offline = true
        val vm = model(auth, ProfileSource { if (offline) throw IOException(); profile() })
        advanceUntilIdle(); vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.PROFILE_ERROR, vm.state.phase)
        assertNull(vm.state.profile)
        offline = false
        vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_IN, vm.state.phase)
    }

    @Test fun profileForAnotherUserCannotUnlockTheApp() = runTest(dispatcher) {
        val vm = model(Auth(SessionAccount("a", "a@uniandes.edu.co", true)), ProfileSource { profile("b@uniandes.edu.co") })
        advanceUntilIdle(); vm.loadProfile(); advanceUntilIdle()
        assertEquals(SessionPhase.PROFILE_ERROR, vm.state.phase)
        assertNull(vm.state.profile)
    }

    @Test fun lateProfileResponseCannotRestoreDataAfterLogout() = runTest(dispatcher) {
        val result = CompletableDeferred<UserProfile>()
        val vm = model(Auth(SessionAccount("a", "a@uniandes.edu.co", true)), ProfileSource { result.await() })
        advanceUntilIdle(); vm.loadProfile(); runCurrent()
        vm.signOut()
        result.complete(profile()); advanceUntilIdle()
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
        assertNull(vm.state.profile)
    }

    @Test fun accountChangeClearsPreviousProfileBeforeNewRequests() = runTest(dispatcher) {
        val auth = Auth(SessionAccount("a", "a@uniandes.edu.co", true))
        val vm = model(auth)
        advanceUntilIdle(); vm.loadProfile(); advanceUntilIdle()
        auth.change(SessionAccount("b", "b@uniandes.edu.co", true))
        assertEquals(SessionPhase.CHECKING, vm.state.phase)
        assertNull(vm.state.profile)
        advanceUntilIdle()
        assertEquals("b", vm.state.uid)
        assertEquals(SessionPhase.PROFILE_LOADING, vm.state.phase)
    }

    @Test fun resetConfirmationDoesNotClaimAnAccountExists() = runTest(dispatcher) {
        val auth = Auth()
        val vm = model(auth)
        vm.resetPassword("a@uniandes.edu.co"); advanceUntilIdle()
        assertEquals(1, auth.resets)
        assertTrue(vm.state.message!!.startsWith("If an account exists"))
        assertNull(auth.current)
        assertEquals(SessionPhase.SIGNED_OUT, vm.state.phase)
    }
}

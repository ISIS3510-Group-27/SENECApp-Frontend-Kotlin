package com.senecapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.senecapp.auth.SessionAuth
import com.senecapp.auth.SessionRequiredException
import com.senecapp.auth.invalidFirebaseSession
import com.senecapp.auth.isUniversityEmail
import com.senecapp.auth.normalizedUniversityEmail
import com.senecapp.data.ProfileSource
import com.senecapp.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException

internal enum class SessionPhase { CHECKING, SIGNED_OUT, VERIFICATION, PROFILE_LOADING, PROFILE_ERROR, SIGNED_IN, SESSION_ERROR }
internal data class SessionUiState(val configured: Boolean, val phase: SessionPhase = SessionPhase.CHECKING,
    val uid: String? = null, val profile: UserProfile? = null, val busy: Boolean = false,
    val error: String? = null, val message: String? = null)

internal class SessionViewModel(private val auth: SessionAuth, private val profiles: ProfileSource) : ViewModel() {
    var state by mutableStateOf(SessionUiState(configured = auth.configured))
        private set
    private var authRequest: Job? = null
    private var profileRequest: Job? = null
    private var observedUid = auth.account()?.uid
    private val stopObserving = auth.observe {
        val uid = auth.account()?.uid
        if (uid != observedUid) {
            observedUid = uid
            if (authRequest?.isActive != true) {
                if (uid == null) signOut("Your session has ended. Sign in again.") else checkSession()
            }
        }
    }

    init { checkSession() }

    fun signIn(email: String, password: String) {
        if (state.busy || !auth.configured) return
        if (!isUniversityEmail(email)) {
            state = state.copy(error = "Use your @uniandes.edu.co email address.", message = null)
            return
        }
        if (password.isEmpty()) { state = state.copy(error = "Enter your password.", message = null); return }
        state = SessionUiState(auth.configured, busy = true)
        authRequest = viewModelScope.launch {
            try {
                auth.signIn(normalizedUniversityEmail(email), password)
                validateAccount()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                auth.signOut()
                state = SessionUiState(auth.configured, SessionPhase.SIGNED_OUT, error = signInMessage(failure))
            } finally { observedUid = auth.account()?.uid }
        }
    }

    fun checkSession() {
        if (authRequest?.isActive == true) return
        profileRequest?.cancel()
        if (!auth.configured || auth.account() == null) {
            state = SessionUiState(auth.configured, SessionPhase.SIGNED_OUT)
            return
        }
        state = SessionUiState(auth.configured, busy = true)
        authRequest = viewModelScope.launch {
            try { validateAccount()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (invalidFirebaseSession(failure) || failure is SessionRequiredException) {
                    auth.signOut()
                    state = SessionUiState(auth.configured, SessionPhase.SIGNED_OUT,
                        error = "Your session has ended. Sign in again.")
                } else {
                    state = SessionUiState(auth.configured, SessionPhase.SESSION_ERROR,
                        error = "Could not check your session. Check your internet connection and retry.")
                }
            } finally { observedUid = auth.account()?.uid }
        }
    }

    private suspend fun validateAccount() {
        val expectedUid = auth.account()?.uid ?: throw SessionRequiredException()
        auth.reload()
        val account = auth.account()?.takeIf { it.uid == expectedUid } ?: throw SessionRequiredException()
        if (!isUniversityEmail(account.email)) {
            auth.signOut()
            state = SessionUiState(auth.configured, SessionPhase.SIGNED_OUT,
                error = "Only @uniandes.edu.co accounts can access SENECApp.")
        } else if (!account.verified) {
            state = SessionUiState(auth.configured, SessionPhase.VERIFICATION, uid = account.uid)
        } else {
            auth.refreshToken()
            if (auth.account()?.uid != expectedUid) throw SessionRequiredException()
            // /me starts only after LocalBackendAccess has granted local network access.
            state = SessionUiState(auth.configured, SessionPhase.PROFILE_LOADING, uid = account.uid)
        }
    }

    fun loadProfile() {
        if (state.phase !in setOf(SessionPhase.PROFILE_LOADING, SessionPhase.PROFILE_ERROR, SessionPhase.SIGNED_IN) ||
            profileRequest?.isActive == true) return
        val uid = state.uid ?: return
        val refreshing = state.phase == SessionPhase.SIGNED_IN
        state = state.copy(phase = if (refreshing) SessionPhase.SIGNED_IN else SessionPhase.PROFILE_LOADING,
            busy = true, error = null)
        profileRequest = viewModelScope.launch {
            try {
                val profile = profiles.load()
                val account = auth.account()?.takeIf { it.uid == uid } ?: throw SessionRequiredException()
                check(normalizedUniversityEmail(profile.email) == normalizedUniversityEmail(account.email)) {
                    "The backend returned a different account. Check the Firebase project configuration."
                }
                state = state.copy(phase = SessionPhase.SIGNED_IN, profile = profile, busy = false, error = null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (auth.account()?.uid != uid || failure is SessionRequiredException) {
                    signOut("Your session has ended. Sign in again.")
                } else {
                    state = state.copy(phase = if (refreshing && failure is IOException) SessionPhase.SIGNED_IN else SessionPhase.PROFILE_ERROR,
                        profile = if (refreshing && failure is IOException) state.profile else null, busy = false,
                        error = if (failure is IOException) "Cannot reach the backend. Check the connection and tap Retry."
                            else failure.message ?: "Could not load your profile. Please retry.")
                }
            }
        }
    }

    fun resetPassword(email: String) {
        if (state.busy || !auth.configured) return
        if (!isUniversityEmail(email)) { state = state.copy(error = "Use your @uniandes.edu.co email address.", message = null); return }
        state = state.copy(busy = true, error = null, message = null)
        authRequest = viewModelScope.launch {
            try {
                auth.resetPassword(normalizedUniversityEmail(email))
                resetConfirmation()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (failure is FirebaseAuthException && failure.errorCode == "ERROR_USER_NOT_FOUND") resetConfirmation()
                else state = state.copy(busy = false, error = resetMessage(failure))
            }
        }
    }

    private fun resetConfirmation() {
        state = state.copy(busy = false, message = "If an account exists for this email, a reset link has been sent. Check your inbox and spam folder.")
    }

    fun clearMessage() { if (!state.busy) state = state.copy(error = null, message = null) }

    fun signOut(message: String? = null) {
        authRequest?.cancel()
        profileRequest?.cancel()
        observedUid = null
        state = SessionUiState(auth.configured, SessionPhase.SIGNED_OUT, error = message)
        auth.signOut()
    }

    override fun onCleared() { stopObserving(); super.onCleared() }
}

private fun signInMessage(failure: Exception): String = when (failure) {
    is FirebaseNetworkException -> "Cannot connect to Firebase. Check your internet connection and retry."
    is FirebaseTooManyRequestsException -> "Too many requests. Wait a few minutes and try again."
    is FirebaseAuthException -> when (failure.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is unavailable. Contact the project administrator."
        "ERROR_INVALID_EMAIL" -> "Check your university email address."
        else -> "Could not sign in. Check your email and password, then try again."
    }
    else -> "Could not complete the request. Please try again."
}

private fun resetMessage(failure: Exception): String = when (failure) {
    is FirebaseNetworkException -> "Cannot connect to Firebase. Check your internet connection and retry."
    is FirebaseTooManyRequestsException -> "Too many requests. Wait a few minutes and try again."
    else -> "Could not send the reset email. Please try again."
}

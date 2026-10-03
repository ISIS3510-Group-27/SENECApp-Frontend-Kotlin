package com.senecapp.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.senecapp.auth.RegistrationRepository
import com.senecapp.auth.normalizedUniversityEmail
import com.senecapp.auth.registrationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class RegistrationUiState(val configured: Boolean = false, val email: String? = null,
    val verified: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val resendAt: Long = 0)

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RegistrationRepository(application)
    var state by mutableStateOf(RegistrationUiState(configured = repository.configured))
        private set

    init { restoreAccount() }

    fun restoreAccount() {
        if (state.busy) return
        val account = repository.account()
        state = state.copy(email = account?.email, verified = account?.verified == true, error = null, message = null)
    }

    fun create(email: String, password: String, confirmation: String) {
        if (state.busy || state.email != null || !state.configured) return
        val validation = registrationError(email, password, confirmation)
        if (validation != null) { state = state.copy(error = validation); return }
        state = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                repository.create(normalizedUniversityEmail(email), password)
                state = state.copy(email = repository.account()?.email)
                // Account creation already succeeded if sending the email fails.
                try {
                    repository.sendVerification()
                    emailSent()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) {
                    state = state.copy(error = "Account created, but the verification email could not be sent. Tap Resend email.")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = registrationMessage(failure))
            } finally { state = state.copy(busy = false) }
        }
    }

    fun resend() {
        if (state.busy || state.verified || state.email == null || SystemClock.elapsedRealtime() < state.resendAt) return
        state = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try { repository.sendVerification(); emailSent()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { state = state.copy(error = registrationMessage(failure))
            } finally { state = state.copy(busy = false) }
        }
    }

    fun checkVerification() {
        if (state.busy || state.email == null || state.verified) return
        state = state.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val verified = repository.checkVerification()
                state = state.copy(verified = verified, message = if (verified) "Your email is verified."
                    else "Not verified yet. Open the link in your inbox, then check again.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { state = state.copy(error = registrationMessage(failure))
            } finally { state = state.copy(busy = false) }
        }
    }

    fun useAnotherAccount() {
        if (state.busy) return
        repository.useAnotherAccount()
        state = RegistrationUiState(configured = repository.configured)
    }

    private fun emailSent() {
        state = state.copy(message = "Verification email sent. Check your inbox and spam folder.",
            resendAt = SystemClock.elapsedRealtime() + 60_000)
    }
}

private fun registrationMessage(failure: Exception): String = when (failure) {
    is FirebaseAuthUserCollisionException -> "This email already has an account. Use the sign-in flow when it is available."
    is FirebaseAuthWeakPasswordException -> "The password does not meet the project's security requirements. Try a stronger password."
    is FirebaseNetworkException -> "Cannot connect to Firebase. Check your internet connection and retry."
    is FirebaseTooManyRequestsException -> "Too many requests. Wait a few minutes and try again."
    is FirebaseAuthException -> when (failure.errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password registration is not enabled. Contact the project administrator."
        "ERROR_INVALID_EMAIL" -> "Check your university email address."
        else -> "Could not complete the request. Please try again."
    }
    else -> "Could not complete the request. Please try again."
}

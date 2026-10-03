package com.senecapp.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class RegistrationAccount(val email: String, val verified: Boolean)

internal class RegistrationRepository(context: Context) {
    private val auth = FirebaseApp.initializeApp(context.applicationContext)?.let { FirebaseAuth.getInstance(it) }
    val configured: Boolean get() = auth != null

    fun account(): RegistrationAccount? = auth?.currentUser?.let {
        RegistrationAccount(it.email.orEmpty(), it.isEmailVerified)
    }

    suspend fun create(email: String, password: String) {
        requireNotNull(auth) { "Registration is unavailable in this build." }
            .createUserWithEmailAndPassword(email, password).awaitResult()
    }

    suspend fun sendVerification() {
        val user = requireNotNull(auth?.currentUser) { "Create an account first." }
        user.sendEmailVerification().awaitResult()
    }

    suspend fun checkVerification(): Boolean {
        val firebaseAuth = requireNotNull(auth) { "Registration is unavailable in this build." }
        val user = requireNotNull(firebaseAuth.currentUser) { "Create an account first." }
        user.reload().awaitResult()
        val verified = firebaseAuth.currentUser?.isEmailVerified == true
        // Refresh email_verified in the SDK's token for the later login integration.
        if (verified) firebaseAuth.currentUser?.getIdToken(true)?.awaitResult()
        return verified
    }

    fun useAnotherAccount() { auth?.signOut() }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resumeWith(Result.success(task.result))
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Request failed.")))
        }
    }
}

package com.senecapp.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun defaultFirebaseAuth(context: Context): FirebaseAuth? =
    FirebaseApp.initializeApp(context.applicationContext)?.let { FirebaseAuth.getInstance() }

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resumeWith(Result.success(task.result))
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Request failed.")))
        }
    }
}

internal fun invalidFirebaseSession(failure: Throwable): Boolean =
    failure is FirebaseAuthInvalidUserException || (failure is FirebaseAuthException &&
        failure.errorCode in setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND"))

internal interface IdTokenProvider {
    fun userId(): String?
    suspend fun token(forceRefresh: Boolean): String
    fun invalidate(expectedUserId: String)
}

internal class SessionRequiredException : IllegalStateException("Your session has ended. Sign in again.")
internal class SessionChangedException : IllegalStateException("The signed-in account changed. Please retry.")

// The SDK stores and renews tokens. No application token cache is kept.
internal object FirebaseIdTokenProvider : IdTokenProvider {
    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    override fun userId(): String? = auth().currentUser?.uid

    override suspend fun token(forceRefresh: Boolean): String {
        val user = auth().currentUser ?: throw SessionRequiredException()
        if (!isUniversityEmail(user.email.orEmpty()) || !user.isEmailVerified) {
            invalidate(user.uid)
            throw SessionRequiredException()
        }
        val token = try {
            user.getIdToken(forceRefresh).awaitResult().token ?: throw SessionRequiredException()
        } catch (failure: Exception) {
            if (invalidFirebaseSession(failure)) invalidate(user.uid)
            throw failure
        }
        if (auth().currentUser?.uid != user.uid) throw SessionChangedException()
        return token
    }

    override fun invalidate(expectedUserId: String) {
        if (auth().currentUser?.uid == expectedUserId) auth().signOut()
    }
}

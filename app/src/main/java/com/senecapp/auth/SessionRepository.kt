package com.senecapp.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import android.app.Activity

internal data class SessionAccount(val uid: String, val email: String, val verified: Boolean)

internal interface SessionAuth {
    val configured: Boolean
    fun account(): SessionAccount?
    fun observe(onChange: () -> Unit): () -> Unit
    suspend fun signIn(email: String, password: String)
    suspend fun signInMicrosoft(activity: Activity) { error("Microsoft sign-in is unavailable.") }
    suspend fun reload()
    suspend fun refreshToken()
    suspend fun resetPassword(email: String)
    fun signOut()
}

internal class SessionRepository(context: Context) : SessionAuth {
    private val auth = defaultFirebaseAuth(context)
    override val configured: Boolean get() = auth != null
    override fun account(): SessionAccount? = auth?.currentUser?.let {
        SessionAccount(it.uid, it.email.orEmpty(), it.isEmailVerified)
    }

    override fun observe(onChange: () -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChange() }
        auth?.addAuthStateListener(listener)
        return { auth?.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String) {
        requireNotNull(auth).signInWithEmailAndPassword(email, password).awaitResult()
    }

    override suspend fun signInMicrosoft(activity: Activity) {
        val firebase = requireNotNull(auth)
        val provider = OAuthProvider.newBuilder("microsoft.com")
            .addCustomParameter("tenant", "uniandes.edu.co")
            .addCustomParameter("prompt", "select_account").build()
        (firebase.pendingAuthResult ?: firebase.startActivityForSignInWithProvider(activity, provider)).awaitResult()
    }

    override suspend fun reload() {
        val user = auth?.currentUser ?: throw SessionRequiredException()
        user.reload().awaitResult()
    }

    override suspend fun refreshToken() { FirebaseIdTokenProvider.token(forceRefresh = true) }
    override suspend fun resetPassword(email: String) {
        requireNotNull(auth).sendPasswordResetEmail(email).awaitResult()
    }
    override fun signOut() { auth?.signOut() }
}

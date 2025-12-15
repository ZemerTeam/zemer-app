package com.jtech.zemer.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication wrapper for Google Sign-In functionality.
 * Handles user authentication, session management, and token refresh.
 */
@Singleton
class UserAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val googleSignInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.jtech.zemer.R.string.default_web_client_id))
            .requestEmail()
            .build()
    }

    val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = auth.currentUser

    val isUserSignedIn: Boolean
        get() = auth.currentUser != null

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    /**
     * Flow that emits authentication state changes
     */
    val authStateFlow: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            val state = if (user != null) {
                AuthState.SignedIn(
                    userId = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    isEmailVerified = user.isEmailVerified
                )
            } else {
                AuthState.SignedOut
            }
            trySend(state)
        }

        auth.addAuthStateListener(listener)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    /**
     * Sign in with Google ID token
     */
    suspend fun signInWithGoogle(idToken: String): Result<com.google.firebase.auth.FirebaseUser> {
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: user is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user
     */
    suspend fun signOut() {
        try {
            auth.signOut()
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            // Log error but don't fail the operation
            // In a real app, you'd want to log this to a crash reporting service
        }
    }

    /**
     * Refresh the current user's ID token
     */
    suspend fun refreshToken(): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            val tokenResult = user.getIdToken(false).await()
            Result.success(tokenResult.token ?: throw Exception("Token is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current user's ID token
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            val tokenResult = user.getIdToken(forceRefresh).await()
            Result.success(tokenResult.token ?: throw Exception("Token is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete the current user's account
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
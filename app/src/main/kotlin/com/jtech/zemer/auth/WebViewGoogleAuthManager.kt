package com.jtech.zemer.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the anonymous Firebase account used for content-filter preference sync.
 * (The class name is historical: the WebView-based Google OAuth flow it once held was
 * removed as dead code - anonymous sign-in is the only auth path this manager provides.)
 */
@Singleton
class WebViewGoogleAuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {

    suspend fun signInAnonymously(): Result<com.google.firebase.auth.FirebaseUser> {
        return try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Anonymous sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e("WebViewAuth", "Anonymous sign in failed", e)
            Result.failure(e)
        }
    }
}

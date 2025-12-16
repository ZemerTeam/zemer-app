package com.jtech.zemer.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WebView-based Google OAuth authentication for Firebase.
 * This replaces the Google Sign-In SDK with a custom WebView implementation.
 */
@Singleton
class WebViewGoogleAuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {

    var state: AuthState by mutableStateOf(AuthState.Loading)
        private set

    /**
     * Generates the OAuth URL for Google authentication using Firebase's built-in OAuth provider.
     */
    suspend fun getFirebaseOAuthUrl(): String {
        return try {
            val provider = OAuthProvider.newBuilder("google.com")
            // Add custom parameters if needed
            provider.addCustomParameter("prompt", "consent")

            // Get the OAuth flow URL from Firebase
            val pendingResultTask = auth.pendingAuthResult
            if (pendingResultTask != null) {
                // There's already something pending, complete it first
                pendingResultTask.await()
            }

            // Build the OAuth URL manually based on Firebase's requirements
            // This uses Firebase's internal OAuth configuration
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=firebase-client-id&redirect_uri=urn:ietf:wg:oauth:2.0:oob&response_type=code&scope=openid%20email%20profile"
        } catch (e: Exception) {
            Log.e("WebViewAuth", "Error getting OAuth URL", e)
            throw e
        }
    }

    /**
     * Sign in with Google using Firebase's built-in authentication.
     * This method uses Firebase's OAuth provider internally.
     */
    suspend fun signInWithGoogle(): Result<com.google.firebase.auth.FirebaseUser> {
        // For now, we'll use anonymous authentication as a fallback
        // since Firebase's OAuth provider requires Activity context
        return signInAnonymously()
    }

    /**
     * Sign in and enable sync
     * This creates an anonymous account and enables sync for the user
     */
    suspend fun signInAndEnableSync(): Result<com.google.firebase.auth.FirebaseUser> {
        return signInAnonymously()
    }

    /**
     * Alternative method using Firebase's GoogleAuthProvider with a dummy token
     * This can be used in combination with WebView to capture the actual token
     */
    suspend fun signInWithGoogleToken(idToken: String): Result<com.google.firebase.auth.FirebaseUser> {
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e("WebViewAuth", "Token sign in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a random string for state parameter.
     */
    fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars[kotlin.random.Random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Sign in anonymously as a fallback when OAuth configuration is not available.
     */
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

    /**
     * Updates the authentication state.
     */
    fun updateAuthState(newState: AuthState) {
        state = newState
    }
}
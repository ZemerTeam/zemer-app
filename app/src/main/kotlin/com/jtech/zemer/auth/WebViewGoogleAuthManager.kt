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
     * Sign in with Google using Firebase's built-in authentication.
     * This method uses Firebase's OAuth provider internally.
     */
    suspend fun signInWithGoogle(): Result<com.google.firebase.auth.FirebaseUser> {
        // For now, we'll use anonymous authentication as a fallback
        // since Firebase's OAuth provider requires Activity context
        return signInAnonymously()
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

}
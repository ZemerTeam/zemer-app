package com.jtech.zemer.auth

/**
 * Sealed class representing the authentication state of a user.
 * Used throughout the app to handle different authentication scenarios.
 */
sealed class AuthState {
    /**
     * User is signed in with account information
     */
    data class SignedIn(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val isEmailVerified: Boolean
    ) : AuthState()

    /**
     * User is signed out
     */
    object SignedOut : AuthState()

    /**
     * Authentication state is loading/unknown
     */
    object Loading : AuthState()

    /**
     * Authentication error occurred
     */
    data class Error(val exception: Throwable) : AuthState()

    /**
     * Helper properties to check current state
     */
    val isSignedIn: Boolean
        get() = this is SignedIn

    val isSignedOut: Boolean
        get() = this is SignedOut

    val isLoading: Boolean
        get() = this is Loading

    val isError: Boolean
        get() = this is Error

    /**
     * Get signed in user data, or null if not signed in
     */
    fun getSignedInUser(): SignedIn? = this as? SignedIn

    /**
     * Get error details, or null if not in error state
     */
    fun getError(): Throwable? = (this as? Error)?.exception
}
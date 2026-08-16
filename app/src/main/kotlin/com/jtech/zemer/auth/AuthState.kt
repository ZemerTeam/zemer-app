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

    val isLoading: Boolean
        get() = this is Loading

    val isError: Boolean
        get() = this is Error

}
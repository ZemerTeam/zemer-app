package com.jtech.zemer.auth

/**
 * Sealed class representing the authentication state of a user.
 * Used throughout the app to handle different authentication scenarios.
 */
sealed class AuthState {
    data class SignedIn(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val isEmailVerified: Boolean
    ) : AuthState()

    object SignedOut : AuthState()

    /**
     * Authentication state is loading/unknown
     */
    object Loading : AuthState()

    data class Error(val exception: Throwable) : AuthState()

    val isSignedIn: Boolean
        get() = this is SignedIn

    val isLoading: Boolean
        get() = this is Loading

    val isError: Boolean
        get() = this is Error

}
package com.jtech.zemer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginGateRedirectTest {
    @Test
    fun `no route yet means the graph is not set - never navigate`() {
        assertFalse(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = false, currentRoute = null))
    }

    @Test
    fun `a logged-out non-relay session on any other route is redirected`() {
        assertTrue(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = false, currentRoute = "home"))
        assertTrue(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = false, currentRoute = "settings"))
    }

    @Test
    fun `already on the gate or the login page is left alone`() {
        assertFalse(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = false, currentRoute = "login_gate"))
        assertFalse(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = false, currentRoute = "login"))
    }

    @Test
    fun `a logged-in or relay session is never bounced`() {
        assertFalse(LoginGateRedirect.shouldRedirect(loggedIn = true, relay = false, currentRoute = "home"))
        assertFalse(LoginGateRedirect.shouldRedirect(loggedIn = false, relay = true, currentRoute = "home"))
    }
}

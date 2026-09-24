package com.jtech.zemer.ui.screens

/**
 * The login-gate redirect decision (`MainActivity`), pure so it is unit-tested. The gate effect fires on
 * every DataStore snapshot AND on every route change; the route is null until `NavHost` has set the graph,
 * and navigating in that window throws ("Cannot navigate to login_gate. Navigation graph has not been set")
 * - a warm start or a config-change recreate delivers the snapshot before the graph exists. A null route
 * therefore means "not yet", never "somewhere else": the effect re-runs on the first real route.
 */
object LoginGateRedirect {
    const val ROUTE = "login_gate"

    fun shouldRedirect(loggedIn: Boolean, relay: Boolean, currentRoute: String?): Boolean {
        if (currentRoute == null) return false
        return !loggedIn && !relay && currentRoute != ROUTE && currentRoute != "login"
    }
}

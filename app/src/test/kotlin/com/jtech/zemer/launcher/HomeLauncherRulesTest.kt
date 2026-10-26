package com.jtech.zemer.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLauncherRulesTest {
    @Test
    fun backIsSwallowedOnlyAtTheRootWhileZemerIsHome() {
        assertTrue(HomeLauncherRules.swallowBack(isDefaultHome = true, hasPreviousEntry = false))
        assertFalse(HomeLauncherRules.swallowBack(isDefaultHome = true, hasPreviousEntry = true)) // inner screens pop normally
        assertFalse(HomeLauncherRules.swallowBack(isDefaultHome = false, hasPreviousEntry = false)) // not home: Back exits as before
    }

    @Test
    fun homePressIsIgnoredDuringSignInAndBeforeTheGraphExists() {
        assertTrue(HomeLauncherRules.handleHomePress("settings/appearance"))
        assertFalse(HomeLauncherRules.handleHomePress("login"))
        assertFalse(HomeLauncherRules.handleHomePress("login_gate"))
        assertFalse(HomeLauncherRules.handleHomePress(null))
    }
}

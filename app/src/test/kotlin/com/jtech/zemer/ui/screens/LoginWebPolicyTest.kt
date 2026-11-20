package com.jtech.zemer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign-in WebView loads only what a Google sign-in needs; Help, Guest mode and store links go nowhere. */
class LoginWebPolicyTest {
    private fun allows(url: String): Boolean {
        val m = Regex("^([a-zA-Z]+)://([^/?#]*)([^?#]*)").find(url)!!
        return LoginWebPolicy.allows(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    @Test fun signInFlowIsAllowed() {
        listOf(
            "https://accounts.google.com/ServiceLogin",
            "https://accounts.google.com/v3/signin/_/AccountsSignInUi/data/batchexecute",
            "https://accounts.google.com/CheckCookie",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://music.youtube.com/",
            "https://consent.youtube.com/m",
            "https://gds.google.com/web/recoveryoptions",
            "https://signaler-pa.googleapis.com/punctual/v1/chooseServer",
            "https://www.gstatic.com/_/mss/boq-identity/_/js/k=boq-identity",
            "https://fonts.googleapis.com/css2",
            "https://fonts.gstatic.com/s/roboto/v18/a.woff2",
            "https://ssl.gstatic.com/images/a.png",
            "https://www.google.com/recaptcha/enterprise/anchor",
            "https://ACCOUNTS.google.com/ServiceLogin",
        ).forEach { assertTrue(it, allows(it)) }
    }

    @Test fun everythingElseIsRefused() {
        listOf(
            // The reported escape: Guest mode help panel, then Workspace and the Play store.
            "https://www.google.com/tools/feedback/repeater_help_panel",
            "https://support.google.com/chrome/answer/6130773",
            "https://support.google.com/apis/render",
            "https://workspace.google.com/",
            "https://play.google.com/store/apps",
            "https://policies.google.com/privacy",
            "https://www.google.com/search?q=x",
            "https://www.google.com/",
            "https://www.youtube.com/",
            "https://myaccount.google.com/",
            "https://googleads.g.doubleclick.net/pagead/id",
            "http://accounts.google.com/ServiceLogin",
            "intent://scan/#Intent;scheme=zxing;end",
            "https://accounts.google.com.evil.example/",
            "https://evil.example/accounts.google.com",
        ).forEach { assertFalse(it, allows(it)) }
        assertFalse(LoginWebPolicy.allows("https", null, "/"))
    }

    @Test fun onlyTheLandingIsHidden() {
        assertTrue(LoginWebPolicy.isLanding("music.youtube.com"))
        assertFalse(LoginWebPolicy.isLanding("accounts.google.com"))
        assertFalse(LoginWebPolicy.isLanding(null))
    }
}

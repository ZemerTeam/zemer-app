package com.jtech.zemer.tracking

import androidx.datastore.preferences.core.MutablePreferences
import com.jtech.zemer.constants.AcceptedPrivacyAtKey
import com.jtech.zemer.constants.AcceptedPrivacyVersionKey

/**
 * The privacy policy version gate. A new install agrees during onboarding; an install that finished onboarding
 * under an older policy is blocked by the current one until it taps Agree (owner decision: no decline). The
 * version and time agreed are stored, and reach the server with the settings in every tracking batch.
 * Bump [CURRENT_VERSION] whenever the policy text adds what the app collects.
 */
object PrivacyNotice {
    const val CURRENT_VERSION = 2

    fun shouldShow(onboardingComplete: Boolean, accepted: Int): Boolean =
        onboardingComplete && accepted < CURRENT_VERSION

    fun recordAgreement(prefs: MutablePreferences, now: Long = System.currentTimeMillis()) {
        prefs[AcceptedPrivacyVersionKey] = CURRENT_VERSION
        prefs[AcceptedPrivacyAtKey] = now
    }
}

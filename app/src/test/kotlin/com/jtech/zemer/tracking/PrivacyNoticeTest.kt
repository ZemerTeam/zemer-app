package com.jtech.zemer.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyNoticeTest {
    @Test
    fun `blocks only an onboarded install that agreed to an older policy`() {
        assertTrue(PrivacyNotice.shouldShow(onboardingComplete = true, accepted = 0))
        assertTrue(PrivacyNotice.shouldShow(onboardingComplete = true, accepted = PrivacyNotice.CURRENT_VERSION - 1))
        assertFalse(PrivacyNotice.shouldShow(onboardingComplete = true, accepted = PrivacyNotice.CURRENT_VERSION))
        assertFalse(PrivacyNotice.shouldShow(onboardingComplete = false, accepted = 0)) // onboarding asks instead
    }
}

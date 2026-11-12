package com.jtech.zemer.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the onboarding step order and its skip-when-already-configured branching. */
class OnboardingNavigationTest {

    @Test
    fun firstStep_densityComesBeforeTheTerms() {
        // a flip phone can't reach Welcome's Continue button before the display is scaled down
        assertEquals(OnboardingStep.Density, OnboardingNavigation.firstStep(densityAlreadySet = false))
        assertEquals(OnboardingStep.Welcome, OnboardingNavigation.firstStep(densityAlreadySet = true))
    }

    @Test
    fun afterWelcome_skipsContentFiltersOnlyWhenSet() {
        assertEquals(OnboardingStep.ContentFilters, OnboardingNavigation.afterWelcome(contentFiltersAlreadySet = false))
        assertEquals(OnboardingStep.Permissions, OnboardingNavigation.afterWelcome(contentFiltersAlreadySet = true))
    }

    @Test
    fun backFromPermissions_neverLandsOnASkippedStep() {
        assertEquals(OnboardingStep.ContentFilters, OnboardingNavigation.backFromPermissions(false, filtersRestored = false))
        assertEquals(OnboardingStep.Welcome, OnboardingNavigation.backFromPermissions(true, filtersRestored = false))
        // restored filters make the filter step skip itself forward again, so Back must not land on it
        assertEquals(OnboardingStep.Welcome, OnboardingNavigation.backFromPermissions(false, filtersRestored = true))
    }

    @Test
    fun densityChanged_onlyWhenTheSelectionDiffersFromTheScaleInUse() {
        assertFalse(OnboardingNavigation.densityChanged(selected = 1.0f, stored = 1.0f))
        // custom 100% on a native phone is no change: continue, no restart
        assertFalse(OnboardingNavigation.densityChanged(selected = 100 / 100f, stored = 1.0f))
        assertFalse(OnboardingNavigation.densityChanged(selected = 85 / 100f, stored = 0.85f))
        assertTrue(OnboardingNavigation.densityChanged(selected = 0.75f, stored = 1.0f))
        // going back to native after a scale was applied is a change too
        assertTrue(OnboardingNavigation.densityChanged(selected = 1.0f, stored = 0.75f))
    }
}

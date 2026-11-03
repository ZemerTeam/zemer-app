package com.jtech.zemer.ui.screens.onboarding

import org.junit.Assert.assertEquals
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
        assertEquals(OnboardingStep.ContentFilters, OnboardingNavigation.backFromPermissions(contentFiltersAlreadySet = false))
        assertEquals(OnboardingStep.Welcome, OnboardingNavigation.backFromPermissions(contentFiltersAlreadySet = true))
    }
}

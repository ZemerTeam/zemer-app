package com.jtech.zemer.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests for the onboarding skip-when-already-configured step branching. */
class OnboardingNavigationTest {

    @Test
    fun afterWelcome_freshInstall_goesToDensity() {
        assertEquals(
            OnboardingStep.Density,
            OnboardingNavigation.afterWelcome(densityAlreadySet = false, contentFiltersAlreadySet = false),
        )
    }

    @Test
    fun afterWelcome_densitySet_skipsToContentFilters() {
        assertEquals(
            OnboardingStep.ContentFilters,
            OnboardingNavigation.afterWelcome(densityAlreadySet = true, contentFiltersAlreadySet = false),
        )
    }

    @Test
    fun afterWelcome_bothSet_skipsToPermissions() {
        assertEquals(
            OnboardingStep.Permissions,
            OnboardingNavigation.afterWelcome(densityAlreadySet = true, contentFiltersAlreadySet = true),
        )
    }

    @Test
    fun afterWelcome_onlyFiltersSet_stillGoesToDensity() {
        // Density is not configured, so it must be shown even when filters already are.
        assertEquals(
            OnboardingStep.Density,
            OnboardingNavigation.afterWelcome(densityAlreadySet = false, contentFiltersAlreadySet = true),
        )
    }

    @Test
    fun afterDensity_filtersUnset_goesToContentFilters() {
        assertEquals(OnboardingStep.ContentFilters, OnboardingNavigation.afterDensity(contentFiltersAlreadySet = false))
    }

    @Test
    fun afterDensity_filtersSet_skipsToPermissions() {
        assertEquals(OnboardingStep.Permissions, OnboardingNavigation.afterDensity(contentFiltersAlreadySet = true))
    }

    @Test
    fun backFromContentFilters_densityShown_returnsToDensity() {
        assertEquals(OnboardingStep.Density, OnboardingNavigation.backFromContentFilters(densityAlreadySet = false))
    }

    @Test
    fun backFromContentFilters_densitySkipped_returnsToWelcome() {
        // Density was skipped as already-set, so Back must not land on a screen that was never shown.
        assertEquals(OnboardingStep.Welcome, OnboardingNavigation.backFromContentFilters(densityAlreadySet = true))
    }
}

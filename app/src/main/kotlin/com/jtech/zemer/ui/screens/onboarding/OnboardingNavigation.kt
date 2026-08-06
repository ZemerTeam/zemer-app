package com.jtech.zemer.ui.screens.onboarding

/** The ordered onboarding steps. Public so the pure transition logic below is unit-testable. */
enum class OnboardingStep { Welcome, Density, ContentFilters, Permissions, BottomNavSetup, SearchBackup, Loading }

/**
 * Pure step-transition logic for [com.jtech.zemer.ui.screens.OnboardingFlow], lifted out of the
 * composable so the skip-when-already-configured branching is JVM-testable with no Compose/Android
 * runtime. [densityAlreadySet]/[contentFiltersAlreadySet] are read once from prefs by the flow and
 * threaded in; every transition that depends on them lives here, so the flow composable is just a
 * `when(step)` render.
 */
object OnboardingNavigation {
    /** Welcome -> the first step the user has not already configured. */
    fun afterWelcome(densityAlreadySet: Boolean, contentFiltersAlreadySet: Boolean): OnboardingStep = when {
        densityAlreadySet && contentFiltersAlreadySet -> OnboardingStep.Permissions
        densityAlreadySet -> OnboardingStep.ContentFilters
        else -> OnboardingStep.Density
    }

    /** Density (skip/apply) -> content filters, unless those are already set. */
    fun afterDensity(contentFiltersAlreadySet: Boolean): OnboardingStep =
        if (contentFiltersAlreadySet) OnboardingStep.Permissions else OnboardingStep.ContentFilters

    /** Content filters back -> Density, unless it was skipped as already-set (then Welcome). */
    fun backFromContentFilters(densityAlreadySet: Boolean): OnboardingStep =
        if (densityAlreadySet) OnboardingStep.Welcome else OnboardingStep.Density
}

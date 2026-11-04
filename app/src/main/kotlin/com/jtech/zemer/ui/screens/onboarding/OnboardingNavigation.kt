package com.jtech.zemer.ui.screens.onboarding

/** The ordered onboarding steps. Public so the pure transition logic below is unit-testable. */
enum class OnboardingStep { Density, Welcome, ContentFilters, Permissions, BottomNavSetup, SearchBackup, Loading }

/**
 * Pure step-transition logic for [com.jtech.zemer.ui.screens.OnboardingFlow], lifted out of the
 * composable so the skip-when-already-configured branching is JVM-testable with no Compose/Android
 * runtime. [densityAlreadySet]/[contentFiltersAlreadySet] are read once from prefs by the flow and
 * threaded in; every transition that depends on them lives here, so the flow composable is just a
 * `when(step)` render.
 */
object OnboardingNavigation {
    /**
     * Density comes first: on a small flip phone the Welcome step's agree checkbox and Continue button can sit
     * off-screen until the display is scaled down. Skipped once a density is set (applying one restarts the app).
     */
    fun firstStep(densityAlreadySet: Boolean): OnboardingStep =
        if (densityAlreadySet) OnboardingStep.Welcome else OnboardingStep.Density

    /** Welcome -> content filters, unless those are already set. */
    fun afterWelcome(contentFiltersAlreadySet: Boolean): OnboardingStep =
        if (contentFiltersAlreadySet) OnboardingStep.Permissions else OnboardingStep.ContentFilters

    /** Permissions back -> ContentFilters if it was shown, else Welcome (never a step skipped as already-set). */
    fun backFromPermissions(contentFiltersAlreadySet: Boolean): OnboardingStep =
        if (contentFiltersAlreadySet) OnboardingStep.Welcome else OnboardingStep.ContentFilters
}

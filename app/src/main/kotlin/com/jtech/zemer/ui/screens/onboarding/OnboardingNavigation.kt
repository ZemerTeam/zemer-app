package com.jtech.zemer.ui.screens.onboarding

import kotlin.math.abs

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
     * off-screen until the display is scaled down. Skipped once a density is set (applying one restarts the app);
     * Welcome's Back still returns to it.
     */
    fun firstStep(densityAlreadySet: Boolean): OnboardingStep =
        if (densityAlreadySet) OnboardingStep.Welcome else OnboardingStep.Density

    /** Welcome -> content filters, unless those are already set. */
    fun afterWelcome(contentFiltersAlreadySet: Boolean): OnboardingStep =
        if (contentFiltersAlreadySet) OnboardingStep.Permissions else OnboardingStep.ContentFilters

    /**
     * Permissions back -> ContentFilters only when it is a step the user can stay on: it skips itself forward when
     * the filters were already set, and again once saved filters were restored ([filtersRestored]). Else Welcome.
     */
    fun backFromPermissions(contentFiltersAlreadySet: Boolean, filtersRestored: Boolean): OnboardingStep =
        if (contentFiltersAlreadySet || filtersRestored) OnboardingStep.Welcome else OnboardingStep.ContentFilters

    /**
     * Whether the Density step's selection differs from the scale in use, so it must be applied (with a restart)
     * rather than continued past. Custom 100% on a native-scale phone is no change.
     */
    fun densityChanged(selected: Float, stored: Float): Boolean = abs(selected - stored) > 0.001f
}

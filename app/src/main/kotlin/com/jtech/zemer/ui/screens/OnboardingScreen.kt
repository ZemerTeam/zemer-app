package com.jtech.zemer.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jtech.zemer.viewmodels.OnboardingViewModel
import com.jtech.zemer.ui.screens.onboarding.OnboardingSearchBackupScreen
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.ui.screens.onboarding.WelcomeScreen
import com.jtech.zemer.ui.screens.onboarding.DensityScreen
import com.jtech.zemer.ui.screens.onboarding.ContentFiltersScreen
import com.jtech.zemer.ui.screens.onboarding.PermissionsScreen
import com.jtech.zemer.ui.screens.onboarding.BottomNavSetupScreen

private enum class OnboardingStep { Welcome, Density, ContentFilters, Permissions, BottomNavSetup, SearchBackup, Loading }

@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val densityAlreadySet = remember {
        val prefs = context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE)
        prefs.getFloat("density_scale_factor", 1.0f) != 1.0f
    }

    // Simple check if filters are already set
    val contentFiltersAlreadySet = remember {
        context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE).let { prefs ->
            prefs.contains(EnableContentFiltersKey.name) &&
            prefs.contains(AllowFemaleSingersKey.name) &&
            prefs.contains(BlockVideosKey.name)
        }
    }

    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }

    when (step) {
        OnboardingStep.Welcome -> WelcomeScreen(
            onContinue = {
                step = when {
                    densityAlreadySet && contentFiltersAlreadySet -> OnboardingStep.Permissions
                    densityAlreadySet -> OnboardingStep.ContentFilters
                    else -> OnboardingStep.Density
                }
            }
        )

        OnboardingStep.Density -> DensityScreen(
            onSkip = {
                step = if (contentFiltersAlreadySet) OnboardingStep.Permissions else OnboardingStep.ContentFilters
            },
            onBack = { step = OnboardingStep.Welcome }
        )

        OnboardingStep.ContentFilters -> ContentFiltersScreen(
            onSkip = { step = OnboardingStep.Permissions },
            onBack = { step = if (densityAlreadySet) OnboardingStep.Welcome else OnboardingStep.Density },
            viewModel = viewModel,
            contentFiltersAlreadySet = contentFiltersAlreadySet
        )

        OnboardingStep.Permissions -> PermissionsScreen(
            onBack = { step = OnboardingStep.ContentFilters },
            onComplete = { step = OnboardingStep.BottomNavSetup }
        )

        OnboardingStep.BottomNavSetup -> BottomNavSetupScreen(
            onBack = { step = OnboardingStep.Permissions },
            onComplete = { step = OnboardingStep.SearchBackup }
        )

        OnboardingStep.SearchBackup -> OnboardingSearchBackupScreen(
            onBack = { step = OnboardingStep.BottomNavSetup },
            onComplete = { step = OnboardingStep.Loading }
        )

        OnboardingStep.Loading -> LoadingScreen(
            onFinished = onFinished
        )
    }
}


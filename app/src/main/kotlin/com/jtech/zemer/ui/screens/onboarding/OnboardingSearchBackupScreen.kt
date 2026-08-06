package com.jtech.zemer.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jtech.zemer.R
import com.jtech.zemer.viewmodels.OfflineSearchSettingsViewModel
import com.jtech.zemer.ui.component.OnboardingChoiceCard
import com.jtech.zemer.ui.component.OnboardingStepHeader
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton

/**
 * Onboarding step offering the offline search backup — every new user learns the feature exists
 * BEFORE the first server outage, not from a failed search. Enable-selected by default; declining
 * also silences the one-time search-screen promo ([OfflineSearchSettingsViewModel.dismissPromo]) so
 * an explicit "no" is not re-asked. The download itself runs on the syncer's own scope, so leaving
 * onboarding never cancels it.
 */
@Composable
fun OnboardingSearchBackupScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: OfflineSearchSettingsViewModel = hiltViewModel(),
) {
    var enableBackup by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f),
        ) {
            OnboardingStepHeader(
                title = stringResource(R.string.onboarding_backup_title),
                subtitle = stringResource(R.string.onboarding_backup_question),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingChoiceCard(
                    selected = enableBackup,
                    title = stringResource(R.string.onboarding_backup_enable),
                    description = stringResource(R.string.onboarding_backup_enable_desc),
                    onSelect = { enableBackup = true },
                )
                OnboardingChoiceCard(
                    selected = !enableBackup,
                    title = stringResource(R.string.onboarding_backup_skip),
                    description = stringResource(R.string.onboarding_backup_skip_desc),
                    onSelect = { enableBackup = false },
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnboardingPrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = {
                        if (enableBackup) viewModel.setEnabled(true) else viewModel.dismissPromo()
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OnboardingTextButton(
                    text = stringResource(R.string.onboarding_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

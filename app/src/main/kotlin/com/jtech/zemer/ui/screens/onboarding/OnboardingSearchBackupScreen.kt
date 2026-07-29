package com.jtech.zemer.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jtech.zemer.R
import com.jtech.zemer.viewmodels.OfflineSearchSettingsViewModel

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
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_backup_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.onboarding_backup_question),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupChoiceCard(
                    selected = enableBackup,
                    title = stringResource(R.string.onboarding_backup_enable),
                    description = stringResource(R.string.onboarding_backup_enable_desc),
                    onSelect = { enableBackup = true },
                )
                BackupChoiceCard(
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
                Button(
                    onClick = {
                        if (enableBackup) viewModel.setEnabled(true) else viewModel.dismissPromo()
                        onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_continue),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupChoiceCard(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

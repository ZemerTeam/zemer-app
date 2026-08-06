package com.jtech.zemer.ui.screens.onboarding

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.ui.screens.onboarding.OnboardingChoiceCard
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.ui.component.OnboardingStepHeader
import com.jtech.zemer.ui.component.OnboardingPrimaryButton
import com.jtech.zemer.ui.component.OnboardingTextButton

@Composable
internal fun BottomNavSetupScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var enableBottomNav by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
        ) {
            OnboardingStepHeader(
                title = stringResource(R.string.onboarding_nav_setup),
                subtitle = stringResource(R.string.onboarding_nav_question),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Choice cards — the shared onboarding radio card (D-pad focus treatment included)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnboardingChoiceCard(
                    selected = enableBottomNav,
                    title = stringResource(R.string.onboarding_nav_enable),
                    description = stringResource(R.string.onboarding_nav_enable_desc),
                    onSelect = { enableBottomNav = true },
                )
                OnboardingChoiceCard(
                    selected = !enableBottomNav,
                    title = stringResource(R.string.onboarding_nav_no_thanks),
                    description = stringResource(R.string.onboarding_nav_later),
                    onSelect = { enableBottomNav = false },
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_nav_customize_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )

                OnboardingPrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = {
                        // Save preference using SharedPreferences
                        val prefs = context.getSharedPreferences("metrolist_settings", Context.MODE_PRIVATE)
                        prefs.edit {
                            putBoolean("bottomNavigationBarEnabled", enableBottomNav)
                            // Set default items if enabling
                            if (enableBottomNav) {
                                putString("bottomNavigationItems", "home,search,library")
                            }
                        }
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


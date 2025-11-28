package com.jtech.zemer.ui.screens.settings

import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.models.DpadDirection
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.ButtonSetupStep
import com.jtech.zemer.utils.AccessibilityUtils
import com.jtech.zemer.utils.rememberAccessibilityEnabledState
import com.jtech.zemer.viewmodels.ButtonSetupViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonSetupScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val viewModel: ButtonSetupViewModel = hiltViewModel()
    val accessibilityEnabled by rememberAccessibilityEnabledState()
    val context = LocalContext.current

    if (!accessibilityEnabled) {
        AccessibilityPermissionRequired(
            onOpenSettings = { AccessibilityUtils.openAccessibilitySettings(context) }
        )
    } else {
        val focusManager = LocalFocusManager.current
        val uiState by viewModel.uiState.collectAsState()
        val scrollState = rememberScrollState()
        val currentStep = uiState.step

        DisposableEffect(Unit) {
            onDispose { viewModel.cancelSetup() }
        }

        Box(
            modifier = Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            Text(
                text = stringResource(R.string.dpad_setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::startSetup,
                enabled = currentStep !is ButtonSetupStep.Awaiting && currentStep !is ButtonSetupStep.Priming,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.dpad_setup_start))
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(uiState.step is ButtonSetupStep.Completed) {
                CompletedCard()
            }

            PreferenceGroupTitle(title = stringResource(R.string.dpad_current_mapping))
            DpadDirection.entries.forEach { direction ->
                AssignmentRow(
                    direction = direction,
                    keyCode = uiState.assignments[direction],
                    onClear = { viewModel.clear(direction) }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        val showOverlay = currentStep is ButtonSetupStep.Priming || currentStep is ButtonSetupStep.Awaiting
        if (showOverlay) {
            LaunchedEffect(currentStep) { focusManager.clearFocus(force = true) }
            BackHandler { viewModel.cancelSetup() }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val step = currentStep) {
                        is ButtonSetupStep.Awaiting -> ListeningOverlay(
                            direction = step.direction,
                            stepIndex = step.index,
                            totalSteps = DpadDirection.wizardOrder.size
                        )
                        ButtonSetupStep.Priming -> PrimingOverlay()
                        else -> Unit
                    }
                }
            }
        }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.dpad_setup_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun ListeningOverlay(
    direction: DpadDirection,
    stepIndex: Int,
    totalSteps: Int,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(
                    R.string.dpad_setup_step_progress,
                    stepIndex + 1,
                    totalSteps
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DpadDirectionIcon(direction = direction, modifier = Modifier.size(56.dp))
                Column {
                    Text(
                        text = stringResource(direction.labelRes),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.dpad_setup_listening_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.dpad_setup_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dpad_setup_completed),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.dpad_setup_completed_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AssignmentRow(
    direction: DpadDirection,
    keyCode: Int?,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(direction.labelRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = keyCode?.let { KeyEvent.keyCodeToString(it) }
                    ?: stringResource(R.string.dpad_mapping_not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (keyCode != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.dpad_clear_button))
            }
        }
    }
}

@Composable
private fun DpadDirectionIcon(
    direction: DpadDirection,
    modifier: Modifier = Modifier,
) {
    val drawableRes =
        when (direction) {
            DpadDirection.RIGHT -> R.drawable.arrow_forward
            DpadDirection.LEFT -> R.drawable.arrow_back
            DpadDirection.UP -> R.drawable.arrow_upward
            DpadDirection.DOWN -> R.drawable.arrow_downward
            DpadDirection.CENTER -> R.drawable.radio_button_checked
        }
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun PrimingOverlay() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.dpad_setup_press_any_key),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.dpad_setup_listening_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccessibilityPermissionRequired(
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_setup_accessibility_required),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.button_setup_enable_accessibility))
                }
            }
        }
    }
}
